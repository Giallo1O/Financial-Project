const MAX_EDGE = 2400; // abbastanza per leggere il testo, abbastanza piccolo da restare sotto i 5 MB

/**
 * Decodifica una foto applicando la rotazione EXIF (le foto da telefono spesso sono "di lato").
 * Prova piu' strade perche' Safari piu' vecchi non accettano le opzioni di createImageBitmap.
 * Safari sa decodificare anche le foto HEIC dell'iPhone, che il server non accetta: cosi' diventano JPEG.
 */
async function decode(file) {
  try {
    return await createImageBitmap(file, { imageOrientation: 'from-image' });
  } catch { /* opzione non supportata: si prova senza */ }
  try {
    return await createImageBitmap(file);
  } catch { /* si prova con un elemento <img> */ }
  const url = URL.createObjectURL(file);
  try {
    const img = new Image();
    img.src = url;
    await img.decode(); // il browser applica da solo la rotazione
    return img;
  } finally {
    URL.revokeObjectURL(url);
  }
}

/** Prepara la foto di uno scontrino: ruota, riduce e salva come JPEG. I PDF e cio' che il browser non decodifica restano invariati. */
export async function prepareReceiptFile(file) {
  if (file.type === 'application/pdf') return file;
  try {
    const source = await decode(file);
    const w = source.naturalWidth || source.width;
    const h = source.naturalHeight || source.height;
    const scale = Math.min(1, MAX_EDGE / Math.max(w, h));
    const canvas = document.createElement('canvas');
    canvas.width = Math.round(w * scale);
    canvas.height = Math.round(h * scale);
    canvas.getContext('2d').drawImage(source, 0, 0, canvas.width, canvas.height);
    const blob = await new Promise(resolve => canvas.toBlob(resolve, 'image/jpeg', 0.85));
    return blob ? new File([blob], 'scontrino.jpg', { type: 'image/jpeg' }) : file;
  } catch {
    return file;
  }
}
