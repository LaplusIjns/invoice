import assert from 'node:assert/strict';
import test from 'node:test';
import {
  filterInvoices,
  getInvoicePeriods,
  getInvoiceWinningStatus,
  type FilterableInvoice,
} from '../../main/frontend/invoice-filters.ts';

const invoices: FilterableInvoice[] = [
  { invoiceNumber: 'AB-12345678', invoiceDate: '115年05-06月', result: '六獎' },
  { invoiceNumber: 'CD-87654321', invoiceDate: '115年03-04月', result: '未中獎' },
  { invoiceNumber: 'EF-11223344', invoiceDate: '115年05-06月', result: '中獎資料暫時無法取得' },
];

test('發票號碼忽略大小寫及分隔符並進行部分比對', () => {
  assert.deepEqual(
    filterInvoices(invoices, { invoiceNumber: 'b1234', period: '', winningStatus: 'all' }),
    [invoices[0]],
  );
  assert.deepEqual(
    filterInvoices(invoices, { invoiceNumber: '8765-43', period: '', winningStatus: 'all' }),
    [invoices[1]],
  );
});

test('期別與中獎狀態可組合篩選', () => {
  assert.deepEqual(
    filterInvoices(invoices, { invoiceNumber: '', period: '115年05-06月', winningStatus: 'won' }),
    [invoices[0]],
  );
});

test('未中獎不會包含無法判定的結果', () => {
  assert.deepEqual(
    filterInvoices(invoices, { invoiceNumber: '', period: '', winningStatus: 'not-won' }),
    [invoices[1]],
  );
  assert.equal(getInvoiceWinningStatus(invoices[2].result), 'undetermined');
});

test('期別選項由前端資料去重並由新到舊排列', () => {
  assert.deepEqual(getInvoicePeriods([...invoices, { invoiceNumber: '', invoiceDate: '', result: '處理中' }]), [
    '115年05-06月',
    '115年03-04月',
  ]);
});
