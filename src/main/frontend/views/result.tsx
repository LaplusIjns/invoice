import { ViewConfig } from '@vaadin/hilla-file-router/types.js';
import {
  Grid,
  GridColumn,
  Dialog,
  Button,
  Notification,
  TextField,
  Select,
  FormLayout,
  FormRow,
  Icon,
  TextFieldElement,
} from '@vaadin/react-components';
import { useState, useEffect, useRef, useCallback, useMemo, memo } from 'react';
import { ProcessService } from 'Frontend/generated/endpoints';
import InvoiceDTO from 'Frontend/generated/com/github/laplusijns/InvoiceDTO';
import { useSignal } from '@vaadin/hilla-react-signals';
import {
  getInvoiceVerificationStatus,
  type InvoiceVerificationStatus,
} from 'Frontend/invoice-verification';
import {
  filterInvoices,
  getInvoicePeriods,
  type InvoiceWinningFilter,
} from 'Frontend/invoice-filters';
import './result.css';

type InvoiceProcessingStatus = '排隊中' | '辨識中' | '失敗' | '待確認';

function getProcessingStatus(item: InvoiceDTO): InvoiceProcessingStatus {
  return (
    item as InvoiceDTO & {
      processingStatus?: InvoiceProcessingStatus;
    }
  ).processingStatus ?? '待確認';
}

export const config: ViewConfig = {
  menu: { order: 1, icon: 'line-awesome/svg/money-bill-solid.svg' },
  title: '結果',
};

const InvoiceForm = memo(function InvoiceForm({
  onSubmit,
}: {
  onSubmit: (invoiceNumber: string, period: string) => void;
}) {
  const [invoiceNumber, setInvoiceNumber] = useState('');
  const [period, setPeriod] = useState('');
  const errorMessage = useSignal('');
  const invoicePeriods = useRef<Array<{ label: string; value: string }>>([]);

  useEffect(() => {
    ProcessService.invoicePeriods()
      .then((periods: string[]) => {
        const formatted = periods.map((item) => ({ label: item, value: item }));
        invoicePeriods.current = formatted;
        setPeriod(formatted[0]?.value ?? '');
        if (formatted.length === 0) {
          Notification.show('中獎期別暫時無法取得，請稍後再試', {
            theme: 'warning',
            position: 'top-center',
          });
        }
      })
      .catch((error) => {
        console.error(error);
        Notification.show('中獎期別暫時無法取得，請稍後再試', {
          theme: 'warning',
          position: 'top-center',
        });
      });
  }, []);

  const handleSubmit = useCallback(() => {
    const trimmedInvoiceNumber = invoiceNumber.trim();
    const trimmedPeriod = period.trim();

    if (!/^\d{8}$/.test(trimmedInvoiceNumber)) {
      Notification.show('發票號碼必須為8位數字', {
        theme: 'error',
        position: 'top-center',
      });
      return;
    }

    if (!trimmedPeriod) {
      Notification.show('請選擇期別', {
        theme: 'error',
        position: 'top-center',
      });
      return;
    }

    onSubmit(trimmedInvoiceNumber, trimmedPeriod);
    setInvoiceNumber('');
  }, [invoiceNumber, period, onSubmit]);

  return (
    <FormLayout maxColumns={3} style={{ alignSelf: 'center' }} autoResponsive>
      <FormRow>
        <TextField
          label="發票號碼(純數字)"
          value={invoiceNumber}
          maxlength={8}
          minlength={8}
          allowedCharPattern="[0-9]"
          pattern="[0-9]{8}"
          onValueChanged={(event) => setInvoiceNumber(event.detail.value)}
          onValidated={(event) => {
            const field = event.target as TextFieldElement;
            const { validity } = field.inputElement as HTMLInputElement;
            if (validity.valueMissing) {
              errorMessage.value = '必填欄位';
            } else if (validity.tooShort || validity.tooLong) {
              errorMessage.value = '發票固定8碼';
            } else if (validity.patternMismatch) {
              errorMessage.value = '固定8碼、只能數字';
            } else {
              errorMessage.value = '';
            }
          }}
          errorMessage={errorMessage.value}
          clearButtonVisible
        />
        <Select
          label="期別"
          items={invoicePeriods.current}
          value={period}
          required
          onValueChanged={(event) => setPeriod(event.detail.value)}
        />
        <Button theme="primary" onClick={handleSubmit}>
          提交
        </Button>
      </FormRow>
    </FormLayout>
  );
});

export default function ResultView() {
  const [invoices, setInvoices] = useState<InvoiceDTO[]>([]);
  const [invoiceNumberFilter, setInvoiceNumberFilter] = useState('');
  const [periodFilter, setPeriodFilter] = useState('');
  const [winningFilter, setWinningFilter] = useState<InvoiceWinningFilter>('all');
  const [selectedPreview, setSelectedPreview] = useState<string | null>(null);
  const [reprocessingKeys, setReprocessingKeys] = useState<Set<string>>(() => new Set());
  const jsessionidRef = useRef<string>('');
  const subscriptionRef = useRef<any>(null);

  const periodFilterItems = useMemo(
    () => [
      { label: '全部期別', value: '' },
      ...getInvoicePeriods(invoices).map((period) => ({ label: period, value: period })),
    ],
    [invoices],
  );
  const filteredInvoices = useMemo(
    () =>
      filterInvoices(invoices, {
        invoiceNumber: invoiceNumberFilter,
        period: periodFilter,
        winningStatus: winningFilter,
      }),
    [invoices, invoiceNumberFilter, periodFilter, winningFilter],
  );
  const hasActiveFilters = Boolean(invoiceNumberFilter || periodFilter || winningFilter !== 'all');

  useEffect(() => {
    ProcessService.jsessionId().then((jsessionid: string) => {
      jsessionidRef.current = jsessionid;

      ProcessService.data(jsessionid).then((dtos: InvoiceDTO[]) => {
        setInvoices(dtos);
      });

      subscriptionRef.current = ProcessService.invoiceSubscription(jsessionid).onNext((update: InvoiceDTO) => {
        setInvoices((previous) => {
          const exists = previous.some((item) => item.key === update.key);
          return exists
            ? previous.map((item) => (item.key === update.key ? update : item))
            : [...previous, update];
        });
      });
    });

    return () => {
      subscriptionRef.current?.cancel();
    };
  }, []);

  const handleSubmit = useCallback(async (invoiceNumber: string, period: string) => {
    try {
      await ProcessService.process2(period, invoiceNumber, jsessionidRef.current);
      Notification.show('提交成功', { theme: 'success', position: 'top-center' });
    } catch (error) {
      console.error(error);
      Notification.show('提交失敗', { theme: 'error', position: 'top-center' });
    }
  }, []);

  function ImageRenderer({ item }: Readonly<{ item: InvoiceDTO }>) {
    if (!item.imageUrl) {
      return <span aria-label="沒有發票圖片">—</span>;
    }

    return (
      <img
        src={'thumbnail/' + item.imageUrl}
        alt="Invoice"
        style={{ width: 60, height: 60, objectFit: 'cover', borderRadius: 4, cursor: 'pointer' }}
        onClick={() => setSelectedPreview(item.imageUrl)}
      />
    );
  }

  function QrInvoiceNumbersRenderer({ item }: Readonly<{ item: InvoiceDTO }>) {
    const qrInvoiceNumbers = item.qrInvoiceNumbers ?? [];
    return qrInvoiceNumbers.length > 0 ? (
      <span>{qrInvoiceNumbers.join(', ')}</span>
    ) : (
      <span aria-label="沒有 QR Code 發票號碼">—</span>
    );
  }

  function ProcessingStatusRenderer({ item }: Readonly<{ item: InvoiceDTO }>) {
    const status = getProcessingStatus(item);
    const statusClassNames: Record<InvoiceProcessingStatus, string> = {
      排隊中: 'invoice-status invoice-status--queued',
      辨識中: 'invoice-status invoice-status--recognizing',
      失敗: 'invoice-status invoice-status--failed',
      待確認: 'invoice-status invoice-status--pending',
    };

    return (
      <span className={statusClassNames[status] ?? 'invoice-status'} role="status" aria-label={`辨識狀態：${status}`}>
        {status}
      </span>
    );
  }

  function VerificationRenderer({ item }: Readonly<{ item: InvoiceDTO }>) {
    if (getProcessingStatus(item) !== '待確認') {
      return <span aria-label="尚無比對結果">—</span>;
    }

    const status = getInvoiceVerificationStatus(item.invoiceNumber, item.qrInvoiceNumbers);
    const colors: Record<InvoiceVerificationStatus, string> = {
      通過: 'var(--lumo-success-text-color, #147d36)',
      可疑: 'var(--lumo-error-text-color, #b42318)',
      '無 QR': 'var(--lumo-secondary-text-color, #667085)',
    };

    return <strong style={{ color: colors[status] }}>{status}</strong>;
  }

  function ActionRenderer({ item }: Readonly<{ item: InvoiceDTO }>) {
    const isReprocessing = reprocessingKeys.has(item.key);
    const processingStatus = getProcessingStatus(item);
    const isProcessing = processingStatus === '排隊中' || processingStatus === '辨識中';

    return (
      <div className="flex gap-s p-0 m-0">
        <Button
          theme="primary"
          disabled={isReprocessing || isProcessing || !item.imageUrl}
          className="p-0 m-0"
          onClick={async () => {
            setReprocessingKeys((previous) => new Set(previous).add(item.key));
            try {
              const accepted = await ProcessService.reprocess(item.key);
              Notification.show(accepted ? '已加入重新辨識' : '找不到原始發票圖片', {
                duration: 2000,
                theme: accepted ? 'success' : 'warning',
                position: 'top-center',
              });
            } catch (error) {
              console.error(error);
              Notification.show('無法重新辨識', {
                duration: 2000,
                theme: 'error',
                position: 'top-center',
              });
            } finally {
              setReprocessingKeys((previous) => {
                const next = new Set(previous);
                next.delete(item.key);
                return next;
              });
            }
          }}>
          {isReprocessing ? '送出中…' : isProcessing ? '辨識處理中' : '重新辨識'}
        </Button>
        <Button
          theme="error primary"
          className="p-0 m-0"
          onClick={async () => {
            if (!confirm(`確定要刪除發票 ${item.invoiceNumber} 嗎？`)) {
              return;
            }
            try {
              await ProcessService.deleteInvoice(item.key);
              setInvoices((previous) => previous.filter((invoice) => invoice.key !== item.key));
              Notification.show('成功移除', {
                duration: 2000,
                theme: 'success',
                position: 'top-center',
              });
            } catch (error) {
              console.error('刪除失敗', error);
              Notification.show('刪除失敗', {
                duration: 5000,
                theme: 'error',
                position: 'top-center',
              });
            }
          }}>
          刪除
        </Button>
      </div>
    );
  }

  return (
    <div className="flex flex-col h-full items-center justify-center text-center box-border w-full">
      <InvoiceForm onSubmit={handleSubmit} />
      <h2 className="mb-m">發票結果列表</h2>
      <div className="invoice-filters" role="search" aria-label="發票列表篩選">
        <TextField
          label="發票號碼"
          placeholder="輸入部分號碼"
          value={invoiceNumberFilter}
          clearButtonVisible
          onValueChanged={(event) => setInvoiceNumberFilter(event.detail.value)}
        />
        <Select
          label="期別"
          items={periodFilterItems}
          value={periodFilter}
          onValueChanged={(event) => setPeriodFilter(event.detail.value)}
        />
        <Select
          label="中獎狀態"
          items={[
            { label: '全部狀態', value: 'all' },
            { label: '中獎', value: 'won' },
            { label: '未中獎', value: 'not-won' },
            { label: '尚未判定', value: 'undetermined' },
          ]}
          value={winningFilter}
          onValueChanged={(event) => setWinningFilter(event.detail.value as InvoiceWinningFilter)}
        />
        <Button
          disabled={!hasActiveFilters}
          onClick={() => {
            setInvoiceNumberFilter('');
            setPeriodFilter('');
            setWinningFilter('all');
          }}>
          清除篩選
        </Button>
      </div>
      <div className="invoice-filter-summary" role="status" aria-live="polite">
        顯示 {filteredInvoices.length} 筆，共 {invoices.length} 筆
      </div>
      <Grid items={filteredInvoices} className="w-full" theme="row-stripes wrap-cell-content compact">
        <GridColumn header="操作" renderer={ActionRenderer} autoWidth flexGrow={0} />
        <GridColumn header="圖片" path="imageUrl" renderer={ImageRenderer} flexGrow={0} autoWidth />
        <GridColumn header="狀態" renderer={ProcessingStatusRenderer} autoWidth flexGrow={0} />
        <GridColumn header="發票號碼" path="invoiceNumber" autoWidth flexGrow={2} />
        <GridColumn header="QR Code 發票號碼" renderer={QrInvoiceNumbersRenderer} autoWidth flexGrow={2} />
        <GridColumn header="Paddle / QR 比對" renderer={VerificationRenderer} autoWidth flexGrow={1} />
        <GridColumn header="發票日期" path="invoiceDate" />
        <GridColumn header="結果" path="result" autoWidth flexGrow={2} />
      </Grid>
      {selectedPreview && (
        <Dialog
          headerTitle="發票圖片預覽"
          header={
            <Button
              theme="tertiary-inline icon"
              aria-label="關閉圖片預覽"
              title="關閉"
              onClick={() => setSelectedPreview(null)}>
              <Icon src="line-awesome/svg/times-solid.svg" aria-hidden="true" />
            </Button>
          }
          opened={true}
          onOpenedChanged={(event: any) => {
            if (!event.detail.value) setSelectedPreview(null);
          }}>
          <img
            src={'blob/' + selectedPreview}
            alt="發票圖片預覽"
            style={{ width: '100%', height: 'auto', borderRadius: 8 }}
          />
        </Dialog>
      )}
    </div>
  );
}
