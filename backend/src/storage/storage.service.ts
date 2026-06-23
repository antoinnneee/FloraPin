/** Descripteur d'un upload présigné renvoyé au client. */
export interface PresignedUpload {
  url: string;
  method: 'PUT';
  expiresIn: number; // secondes
}

/**
 * Abstraction du stockage objet des images. L'implémentation MinIO/S3 réelle
 * (URLs présignées) est fournie par NODE-28 ; le module flowers ne dépend que
 * de cette interface.
 */
export abstract class StorageService {
  /** Construit une clé d'objet déterministe pour une nouvelle image. */
  abstract buildKey(ownerId: string, extension: string): string;

  /** URL présignée pour téléverser l'objet (PUT direct par le client). */
  abstract presignUpload(key: string): Promise<PresignedUpload>;

  /** URL présignée de lecture (GET), à durée limitée. */
  abstract presignDownload(key: string): Promise<string>;

  /**
   * Supprime définitivement l'objet [key] du stockage (effacement RGPD —
   * NODE-118). Idempotent : ne lève pas si l'objet est déjà absent.
   */
  abstract delete(key: string): Promise<void>;
}
