import { ViewConfig } from '@vaadin/hilla-file-router/types.js';
import { useEffect, useRef, useState, useCallback } from 'react';
import { Notification, Button } from '@vaadin/react-components';
import { ProcessService } from 'Frontend/generated/endpoints';
import { useBlocker, BlockerFunction } from 'react-router';
import { PhotoTaskQueue, type PhotoTask } from 'Frontend/photo-task-queue';

export const config: ViewConfig = {
  menu: { order: 0, icon: 'line-awesome/svg/camera-solid.svg' },
  title: '相機',
};

// 封裝導航前阻止 hook
export function useBeforeNavigate(when: () => boolean, onBeforeNavigate: () => Promise<void>) {
  const blockFn = useCallback<BlockerFunction>(
    ({ currentLocation, nextLocation }) => when() && currentLocation.pathname !== nextLocation.pathname,
    [when],
  );

  const blocker = useBlocker(blockFn);
  const handlingRef = useRef(false);

  useEffect(() => {
    if (blocker.state === 'blocked' && !handlingRef.current) {
      handlingRef.current = true;
      void onBeforeNavigate()
        .then(() => blocker.proceed())
        .catch((error) => {
          console.error('離開頁面前上傳失敗', error);
          blocker.reset();
        })
        .finally(() => {
          handlingRef.current = false;
        });
    }
  }, [blocker, onBeforeNavigate]);
}

export default function CameraView() {
  const videoRef = useRef<HTMLVideoElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const jsessionidRef = useRef<string | null>(null);

  const [flash, setFlash] = useState(false);
  const flashTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [previewTask, setPreviewTask] = useState<PhotoTask | null>(null);

  // 上傳隊列及處理狀態
  const photoQueueRef = useRef(new PhotoTaskQueue());
  const drainPromiseRef = useRef<Promise<void> | null>(null);
  const activeTaskRef = useRef<PhotoTask | null>(null);

  useEffect(() => {
    ProcessService.jsessionId().then((jsessionid: string) => {
      jsessionidRef.current = jsessionid;
    });
  }, []);

  const getSessionId = useCallback(async () => {
    if (jsessionidRef.current) {
      return jsessionidRef.current;
    }

    const jsessionid = await ProcessService.jsessionId();
    jsessionidRef.current = jsessionid;
    return jsessionid;
  }, []);

  // 處理下一張圖片
  const processNext = useCallback((): Promise<void> => {
    if (drainPromiseRef.current) {
      return drainPromiseRef.current;
    }
    if (!photoQueueRef.current.hasReady()) {
      return Promise.resolve();
    }

    const drainPromise = (async () => {
      let next = photoQueueRef.current.takeNext();
      while (next) {
        activeTaskRef.current = next;
        Notification.show('圖片已進入處理流程', {
          duration: 2000,
          theme: 'success',
          position: 'top-center',
        });

        const start = Date.now();

        try {
          await ProcessService.process(next.image, await getSessionId());
        } catch (e) {
          console.error(e);
          Notification.show('圖片處理失敗', {
            theme: 'error',
            position: 'top-center',
          });
        } finally {
          activeTaskRef.current = null;
          const elapsed = Date.now() - start;
          const remaining = Math.max(0, 100 - elapsed);
          if (remaining > 0) {
            await new Promise((resolve) => globalThis.setTimeout(resolve, remaining));
          }
        }

        next = photoQueueRef.current.takeNext();
      }
    })();

    drainPromiseRef.current = drainPromise.finally(() => {
      drainPromiseRef.current = null;
      if (photoQueueRef.current.hasReady()) {
        void processNext();
      }
    });
    return drainPromiseRef.current;
  }, [getSessionId]);

  const hasPendingTasks = useCallback(() => photoQueueRef.current.hasPending() || drainPromiseRef.current !== null, []);

  const flushPendingTasks = useCallback(async () => {
    Notification.show('正在上傳...', {
      duration: 5000,
      position: 'top-center',
      theme: 'warning',
    });

    if (photoQueueRef.current.flushPreview()) {
      setPreviewTask(null);
    }
    await processNext();
  }, [processNext]);

  // 離開頁面前先把預覽中及等待中的圖片全部交給後端 OCR 隊列
  useBeforeNavigate(hasPendingTasks, flushPendingTasks);

  // 離開頁面時自動上傳
  useEffect(() => {
    const handler = (e: BeforeUnloadEvent) => {
      const pending = photoQueueRef.current.snapshotPending();
      const tasks = activeTaskRef.current ? [activeTaskRef.current, ...pending] : pending;
      if (tasks.length > 0) {
        e.preventDefault();
        tasks.forEach((task) => navigator.sendBeacon('/process', JSON.stringify(task)));
      }
    };
    window.addEventListener('beforeunload', handler);
    return () => window.removeEventListener('beforeunload', handler);
  }, []);

  // 開啟攝像頭
  useEffect(() => {
    const startCamera = async () => {
      try {
        const stream = await navigator.mediaDevices.getUserMedia({
          video: { facingMode: { ideal: 'environment' } },
        });
        if (videoRef.current) videoRef.current.srcObject = stream;
      } catch (err) {
        console.error('無法開啟攝像頭', err);
        Notification.show('無法開啟攝像頭', {
          duration: 5000,
          position: 'top-center',
          theme: 'error',
        });
      }
    };

    startCamera();

    return () => {
      if (videoRef.current?.srcObject) {
        const tracks = (videoRef.current.srcObject as MediaStream).getTracks();
        tracks.forEach((track) => track.stop());
      }
    };
  }, []);

  // 拍照
  const takePhoto = () => {
    const video = videoRef.current;
    const canvas = canvasRef.current;

    if (!video || !canvas) return;
    if (!video.videoWidth || !video.videoHeight) return;

    // 瀏覽器目前實際顯示的 video 尺寸
    const rect = video.getBoundingClientRect();

    const displayWidth = rect.width;
    const displayHeight = rect.height;

    // 攝影機原始尺寸
    const videoWidth = video.videoWidth;
    const videoHeight = video.videoHeight;

    const videoRatio = videoWidth / videoHeight;
    const displayRatio = displayWidth / displayHeight;

    let sourceX = 0;
    let sourceY = 0;
    let sourceWidth = videoWidth;
    let sourceHeight = videoHeight;

    /*
     * 對應 object-fit: cover
     *
     * 原始影片比較寬：
     * 左右會被裁掉
     *
     * 原始影片比較高：
     * 上下會被裁掉
     */
    if (videoRatio > displayRatio) {
      // 裁左右
      sourceHeight = videoHeight;
      sourceWidth = videoHeight * displayRatio;

      sourceX = (videoWidth - sourceWidth) / 2;
      sourceY = 0;
    } else {
      // 裁上下
      sourceWidth = videoWidth;
      sourceHeight = videoWidth / displayRatio;

      sourceX = 0;
      sourceY = (videoHeight - sourceHeight) / 2;
    }

    // 輸出圖片比例跟瀏覽器看到的一樣
    canvas.width = Math.round(displayWidth);
    canvas.height = Math.round(displayHeight);

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    ctx.drawImage(
      video,

      // 從原始 video 哪裡開始擷取
      sourceX,
      sourceY,
      sourceWidth,
      sourceHeight,

      // 畫到 canvas
      0,
      0,
      canvas.width,
      canvas.height,
    );

    const imageData = canvas.toDataURL('image/png');

    const task: PhotoTask = {
      id: crypto.randomUUID(),
      image: imageData,
    };

    // 閃光
    setFlash(true);

    if (flashTimeoutRef.current) {
      clearTimeout(flashTimeoutRef.current);
    }

    flashTimeoutRef.current = globalThis.setTimeout(() => {
      setFlash(false);
      flashTimeoutRef.current = null;
    }, 100);

    const previousTask = photoQueueRef.current.replacePreview(task);
    setPreviewTask(task);
    if (previousTask) {
      void processNext();
    }

    // 5 秒後自動加入處理隊列
    globalThis.setTimeout(() => {
      if (!photoQueueRef.current.confirmPreview(task.id)) {
        return;
      }

      setPreviewTask((current) => (current?.id === task.id ? null : current));
      void processNext();
    }, 5000);
  };

  return (
    <div className="flex flex-col h-full items-center p-l text-center box-border">
      <div style={{ position: 'relative', height: '90vh', width: '100%' }}>
        <video
          ref={videoRef}
          autoPlay
          playsInline
          style={{
            height: '90vh',
            width: '100%',
            objectFit: 'cover',
            display: 'block',
          }}
        />

        {flash && (
          <div
            style={{
              position: 'absolute',
              inset: 0,
              backgroundColor: 'white',
              opacity: 0.6,
              pointerEvents: 'none',
              zIndex: 10,
            }}
          />
        )}

        <Button
          onClick={takePhoto}
          className="text-center bg-contrast-40 border border-error text-error absolute font-bold text-3xl shadow-xs rounded-l p-l"
          style={{ bottom: '3%', left: '50%', transform: 'translateX(-50%)' }}>
          <span className="text-primary-contrast">●</span> 拍照
        </Button>
      </div>

      <canvas ref={canvasRef} style={{ display: 'none' }} />

      {previewTask && (
        <Notification opened={true} position="top-end" theme="contrast no-close-button" duration={0}>
          <div className="flex flex-col">
            <img src={previewTask.image} style={{ width: '20vw' }} />
            <Button
              className="border border-error bg-error-50 font-bold text-2xl"
              onClick={() => {
                if (photoQueueRef.current.discardPreview(previewTask.id)) {
                  setPreviewTask(null);
                }
              }}>
              刪除
            </Button>
          </div>
        </Notification>
      )}
    </div>
  );
}
