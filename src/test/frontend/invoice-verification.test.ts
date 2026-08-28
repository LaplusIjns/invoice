import assert from 'node:assert/strict';
import test from 'node:test';
import { getInvoiceVerificationStatus } from '../../main/frontend/invoice-verification.ts';

test('通過：Paddle 與所有 QR 發票號碼相同', () => {
  assert.equal(getInvoiceVerificationStatus('AB-12345678', ['AB12345678', 'AB-12345678']), '通過');
});

test('可疑：任一 QR 發票號碼與 Paddle 不同', () => {
  assert.equal(getInvoiceVerificationStatus('AB-12345678', ['AB-12345678', 'CD-87654321']), '可疑');
});

test('無 QR：圖片未讀到 QR 發票號碼', () => {
  assert.equal(getInvoiceVerificationStatus('AB-12345678', []), '無 QR');
});
