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
  TextFieldElement,
} from '@vaadin/react-components';
import { useSignal } from '@vaadin/hilla-react-signals';
import { useState, useEffect, useRef } from 'react';
import { ProcessService } from 'Frontend/generated/endpoints';
import InvoiceDTO from 'Frontend/generated/com/github/laplusijns/InvoiceDTO';
export const config: ViewConfig = {
  menu: { order: 1, icon: 'line-awesome/svg/money-bill-solid.svg' },
  title: '結果',
};

export default function ResultView() {
  const [invoices, setInvoices] = useState<InvoiceDTO[]>([]);
  const [selectedPreview, setSelectedPreview] = useState<string | null>(null);
  const jsessionidRef = useRef<any>(null);
  const subscriptionRef = useRef<any>(null);
  const invoicePeriods = useRef<any>(null);
  const [invoiceNumber, setInvoiceNumber] = useState('');
  const [period, setPeriod] = useState('');
  const errorMessage = useSignal('');

  useEffect(() => {
    ProcessService.invoicePeriods().then((periods: string[]) => {
      const formatted = periods.map((item) => ({
        label: item,
        value: item,
      }));
      setPeriod(formatted[0].value);
      invoicePeriods.current = formatted;
    });

    ProcessService.jsessionId().then((jsessionid: string) => {
      jsessionidRef.current = jsessionid;

      ProcessService.data(jsessionid).then((dtos: InvoiceDTO[]) => {
        setInvoices(dtos);
      });

      subscriptionRef.current = ProcessService.invoiceSubscription(jsessionid).onNext((update: InvoiceDTO) => {
        setInvoices((prevTexts) => {
          console.log(prevTexts);
          const exists = prevTexts.some((t) => t.key === update.key);
          return exists ? prevTexts : [...prevTexts, update];
        });
      });
    });

    return () => {
      subscriptionRef.current?.cancel();
    };
  }, []);

  async function onSubmit() {
    const trimmedInvoiceNumber = invoiceNumber.trim();
    const trimmedPeriod = period.trim();
    const invoiceRegex = /^\d{8}$/;
    if (!invoiceRegex.test(trimmedInvoiceNumber)) {
      Notification.show('發票號碼必須為8位數字', {
        theme: 'error',
        position: 'top-center',
      });
      return;
    }

    if (!trimmedInvoiceNumber || !trimmedPeriod) {
      Notification.show('請填寫發票號碼並選擇期別', {
        theme: 'error',
        position: 'top-center',
      });
      return;
    }
    ProcessService.process2(trimmedPeriod, trimmedInvoiceNumber, jsessionidRef.current)
      .then(() => {
        Notification.show('提交成功', { theme: 'success', position: 'top-center' });
        setInvoiceNumber('');
      })
      .catch((e) => {
        console.error(e);
        Notification.show('提交失敗', { theme: 'error', position: 'top-center' });
      });
  }

  function ColRenderer({ item }: Readonly<{ item: InvoiceDTO }>) {
    return (
      <img
        src={'thumbnail/' + item.imageUrl}
        alt="Invoice"
        style={{ width: 60, height: 60, objectFit: 'cover', borderRadius: 4 }}
        onClick={() => setSelectedPreview(item.imageUrl)}
      />
    );
  }

  function DeleteRenderer({ item }: Readonly<{ item: InvoiceDTO }>) {
    return (
      <div className="p-0 m-0">
        <Button
          theme="error primary"
          className="p-0 m-0"
          onClick={async () => {
            console.log(item);
            if (!confirm(`確定要刪除發票 ${item.invoiceNumber} 嗎？`)) {
              return;
            }
            ProcessService.deleteInvoice(item.key).finally(() => {
              Notification.show('成功移除', {
                duration: 2000,
                theme: 'success',
                position: 'top-center',
              });
              setInvoices((prev) => prev.filter((inv) => inv.key !== item.key));
            });
          }}>
          刪除
        </Button>
      </div>
    );
  }

  return (
    <div className="flex flex-col h-full items-center justify-center text-center box-border w-full">
      <FormLayout autoResponsive>
        <FormRow>
          <TextField
            label="發票號碼(純數字)"
            value={invoiceNumber}
            maxlength={8}
            minlength={8}
            allowedCharPattern="\d"
            pattern="^\d{8}$"
            onValueChanged={(e) => setInvoiceNumber(e.detail.value)}
            onValidated={(event) => {
              const field = event.target as TextFieldElement;
              const { validity } = field.inputElement as HTMLInputElement;
              if (validity.valueMissing) {
                errorMessage.value = '必填欄位';
              } else if (validity.tooShort) {
                errorMessage.value = `發票固定8碼`;
              } else if (validity.tooLong) {
                errorMessage.value = `發票固定8碼`;
              } else if (validity.patternMismatch) {
                errorMessage.value = '固定8碼、只能數字';
              } else {
                errorMessage.value = '';
              }
            }}
            errorMessage={errorMessage.value}></TextField>
          <Select label="期別" items={invoicePeriods.current} value={period} required onValueChanged={(e) => setPeriod(e.detail.value)} />
          <Button theme="primary" onClick={onSubmit}>
            提交
          </Button>
        </FormRow>
      </FormLayout>
      <h2 className="mb-m">發票結果列表</h2>
      <Grid items={invoices} className="w-full" theme="row-stripes wrap-cell-content compact">
        <GridColumn header="操作" renderer={DeleteRenderer} autoWidth flexGrow={0} />
        <GridColumn header="圖片" path="imageUrl" renderer={ColRenderer} flexGrow={0} autoWidth />
        <GridColumn header="發票號碼" path="invoiceNumber" autoWidth flexGrow={2} />
        <GridColumn header="發票日期" path="invoiceDate" />
        <GridColumn header="結果" path="result" autoWidth flexGrow={2} />
      </Grid>
      {selectedPreview && (
        <Dialog
          headerTitle="發票圖"
          opened={true}
          onOpenedChanged={(e: any) => {
            if (!e.detail.value) setSelectedPreview(null); // 關閉時清空
          }}>
          <img src={'blob/' + selectedPreview} style={{ width: '100%', height: 'auto', borderRadius: 8 }} />
        </Dialog>
      )}
    </div>
  );
}
