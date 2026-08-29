export type InvoiceWinningFilter = 'all' | 'won' | 'not-won' | 'undetermined';

export interface InvoiceFilterValues {
  invoiceNumber: string;
  period: string;
  winningStatus: InvoiceWinningFilter;
}

export interface FilterableInvoice {
  invoiceNumber: string;
  invoiceDate: string;
  result?: string;
}

const WINNING_RESULTS = new Set(['特別獎', '特獎', '頭獎', '二獎', '三獎', '四獎', '五獎', '六獎']);

function normalizeInvoiceNumber(value: string): string {
  return value.normalize('NFKC').replace(/[^0-9a-z]/gi, '').toUpperCase();
}

export function getInvoiceWinningStatus(result: string | undefined): Exclude<InvoiceWinningFilter, 'all'> {
  if (result && WINNING_RESULTS.has(result)) {
    return 'won';
  }

  return result === '未中獎' ? 'not-won' : 'undetermined';
}

export function filterInvoices<T extends FilterableInvoice>(
  invoices: readonly T[],
  filters: Readonly<InvoiceFilterValues>,
): T[] {
  const invoiceNumberQuery = normalizeInvoiceNumber(filters.invoiceNumber.trim());

  return invoices.filter((invoice) => {
    const matchesInvoiceNumber =
      !invoiceNumberQuery || normalizeInvoiceNumber(invoice.invoiceNumber).includes(invoiceNumberQuery);
    const matchesPeriod = !filters.period || invoice.invoiceDate === filters.period;
    const matchesWinningStatus =
      filters.winningStatus === 'all' || getInvoiceWinningStatus(invoice.result) === filters.winningStatus;

    return matchesInvoiceNumber && matchesPeriod && matchesWinningStatus;
  });
}

export function getInvoicePeriods(invoices: readonly FilterableInvoice[]): string[] {
  return [
    ...new Set(
      invoices
        .map((invoice) => invoice.invoiceDate.trim())
        .filter((period) => period.length > 0 && period !== 'N/A'),
    ),
  ].sort((left, right) => right.localeCompare(left, 'zh-Hant'));
}
