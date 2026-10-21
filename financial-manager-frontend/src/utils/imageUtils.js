const MAX_EDGE = 2400; // abbastanza per leggere il testo, abbastanza piccolo da restare sotto i 5 MB

/**
 * Prepara la foto di uno scontrino: applica la rotazione EXIF (le foto da telefono spesso sono "di lato"),
 * riduce la dimensione e la salva come JPEG. I PDF e i formati che il browser non decodifica restano invariati.
 */
export async function prepareReceiptFile(file) {
  if (file.type === 'application/pdf') return file;
  try {
    const bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' });
    const scale = Math.min(1, MAX_EDGE / Math.max(bitmap.width, bitmap.height));
    const canvas = document.createElement('canvas');
    canvas.width = Math.round(bitmap.width * scale);
    canvas.height = Math.round(bitmap.height * scale);
    canvas.getContext('2d').drawImage(bitmap, 0, 0, canvas.width, canvas.height);
    const blob = await new Promise(resolve => canvas.toBlob(resolve, 'image/jpeg', 0.85));
    return blob ? new File([blob], 'scontrino.jpg', { type: 'image/jpeg' }) : file;
  } catch {
    return file;
  }
}
