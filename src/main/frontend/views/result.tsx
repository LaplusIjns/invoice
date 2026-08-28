import { ViewConfig } from "@vaadin/hilla-file-router/types.js";
import {
  Grid,
  GridColumn,
  Dialog,
  Button,
  Notification,
} from "@vaadin/react-components";
import { useState, useEffect, useRef } from "react";
import { ProcessService } from "Frontend/generated/endpoints";
import InvoiceDTO from "Frontend/generated/com/github/laplusijns/InvoiceDTO";
export const config: ViewConfig = {
  menu: { order: 1, icon: "line-awesome/svg/money-bill-solid.svg" },
  title: "result",
};

export default function ResultView() {
  const [invoices, setInvoices] = useState<InvoiceDTO[]>([]);
  const [selectedPreview, setSelectedPreview] = useState<string | null>(null);
  const [reprocessingKeys, setReprocessingKeys] = useState<Set<string>>(
    () => new Set(),
  );
  const jsessionidRef = useRef<string | null>(null);
  const subscriptionRef = useRef<any>(null);

  useEffect(() => {
    ProcessService.jsessionId().then((jsessionid: string) => {
      jsessionidRef.current = jsessionid;

      ProcessService.data(jsessionid).then((dtos: InvoiceDTO[]) => {
        setInvoices(dtos);
      });

      subscriptionRef.current = ProcessService.invoiceSubscription(
        jsessionid,
      ).onNext((update: InvoiceDTO) => {
        setInvoices((prevTexts) => {
          const exists = prevTexts.some((t) => t.key === update.key);
          return exists
            ? prevTexts.map((invoice) =>
                invoice.key === update.key ? update : invoice,
              )
            : [...prevTexts, update];
        });
      });
    });

    return () => {
      subscriptionRef.current?.cancel();
    };
  }, []);

  function ColRenderer({ item }: Readonly<{ item: InvoiceDTO }>) {
    return (
      <img
        src={"thumbnail/" + item.imageUrl}
        alt="Invoice"
        style={{ width: 60, height: 60, objectFit: "cover", borderRadius: 4 }}
        onClick={() => setSelectedPreview(item.imageUrl)}
      />
    );
  }
  function ActionRenderer({ item }: Readonly<{ item: InvoiceDTO }>) {
    const isReprocessing = reprocessingKeys.has(item.key);

    return (
      <div className="flex gap-s p-0 m-0">
        <Button
          theme="primary"
          disabled={isReprocessing}
          className="p-0 m-0"
          onClick={async () => {
            setReprocessingKeys((previous) => new Set(previous).add(item.key));
            try {
              const accepted = await ProcessService.reprocess(item.key);
              Notification.show(
                accepted ? "已加入重新辨識" : "找不到原始發票圖片",
                {
                  duration: 2000,
                  theme: accepted ? "success" : "warning",
                  position: "top-center",
                },
              );
            } catch (error) {
              console.error(error);
              Notification.show("無法重新辨識", {
                duration: 2000,
                theme: "error",
                position: "top-center",
              });
            } finally {
              setReprocessingKeys((previous) => {
                const next = new Set(previous);
                next.delete(item.key);
                return next;
              });
            }
          }}
        >
          {isReprocessing ? "送出中…" : "重新辨識"}
        </Button>
        <Button
          theme="error primary"
          className="p-0 m-0"
          onClick={async () => {
            console.log(item);
            if (!confirm(`確定要刪除發票 ${item.invoiceNumber} 嗎？`)) {
              return;
            }
            ProcessService.deleteInvoice(item.key).finally(() => {
              Notification.show("成功移除", {
                duration: 2000,
                theme: "success",
                position: "top-center",
              });
              setInvoices((prev) => prev.filter((inv) => inv.key !== item.key));
            });
          }}
        >
          刪除
        </Button>
      </div>
    );
  }

  return (
    <div className="flex flex-col h-full items-center justify-center text-center box-border w-full">
      <h2 className="mb-m">發票結果列表</h2>
      <Grid
        items={invoices}
        className="w-full"
        theme="row-stripes wrap-cell-content compact"
      >
        <GridColumn
          header="操作"
          renderer={ActionRenderer}
          autoWidth
          flexGrow={0}
        />
        <GridColumn
          header="圖片"
          path="imageUrl"
          renderer={ColRenderer}
          flexGrow={0}
          autoWidth
        />
        <GridColumn
          header="發票號碼"
          path="invoiceNumber"
          autoWidth
          flexGrow={2}
        />
        <GridColumn header="發票日期" path="invoiceDate" />
        <GridColumn header="結果" path="result" autoWidth flexGrow={2} />
      </Grid>
      {selectedPreview && (
        <Dialog
          headerTitle="發票圖"
          opened={true}
          onOpenedChanged={(e: any) => {
            if (!e.detail.value) setSelectedPreview(null); // 關閉時清空
          }}
        >
          <img
            src={"blob/" + selectedPreview}
            style={{ width: "100%", height: "auto", borderRadius: 8 }}
          />
        </Dialog>
      )}
    </div>
  );
}
