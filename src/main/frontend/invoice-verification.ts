export type InvoiceVerificationStatus = '通過' | '可疑' | '無 QR';

function normalizeInvoiceNumber(value: string): string {
  return value.replace(/[^a-z0-9]/gi, '').toUpperCase();
}

export function getInvoiceVerificationStatus(
  paddleInvoiceNumber: string,
  qrInvoiceNumbers: readonly string[] | undefined,
): InvoiceVerificationStatus {
  if (!qrInvoiceNumbers || qrInvoiceNumbers.length === 0) {
    return '無 QR';
  }

  const paddleNumber = normalizeInvoiceNumber(paddleInvoiceNumber);
  const allQrNumbersMatch =
    paddleNumber.length > 0 && qrInvoiceNumbers.every((number) => normalizeInvoiceNumber(number) === paddleNumber);
  return allQrNumbersMatch ? '通過' : '可疑';
}
