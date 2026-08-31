export function saveBlob(blob: Blob, fallbackName: string): void {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = fallbackName;
  anchor.click();
  URL.revokeObjectURL(url);
}

export function filenameFromDisposition(header: string | null | undefined, fallback: string): string {
  if (!header) {
    return fallback;
  }
  const utf8 = /filename\*=UTF-8''([^;]+)/i.exec(header);
  if (utf8?.[1]) {
    try {
      return decodeURIComponent(utf8[1]);
    } catch {
      return fallback;
    }
  }
  const quoted = /filename="([^"]+)"/i.exec(header);
  if (quoted?.[1]) {
    return quoted[1];
  }
  const plain = /filename=([^;]+)/i.exec(header);
  return plain?.[1]?.trim() || fallback;
}
