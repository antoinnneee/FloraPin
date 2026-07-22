/** Descripteur d'un upload présigné renvoyé au client. */
export interface PresignedUpload {
  url: string;
  method: 'PUT';
  expiresIn: number; // secondes
}

export interface StoredObject {
  key: string;
  lastModified: Date;
}

/**
 * Abstraction du stockage objet des images. L'implémentation MinIO/S3 réelle
 * (URLs présignées) est fournie par NODE-28 ; le module flowers ne dépend que
 * de cette interface.
 */
export abstract class StorageService {
  /** Construit une clé d'objet déterministe pour une nouvelle image. */
  abstract buildKey(ownerId: string, extension: string): string;

  /** Clé stable, dédupliquée dans le périmètre d'un propriétaire. */
  abstract buildContentKey(
    ownerId: string,
    sha256: string,
    extension: string,
  ): string;

  /** URL présignée pour téléverser l'objet (PUT direct par le client). */
  abstract presignUpload(key: string): Promise<PresignedUpload>;

  /**
   * Téléverse un objet depuis le serveur après validation (ou après encodage
   * pour les seuls endpoints historiques de compatibilité).
   */
  abstract putObject(
    key: string,
    body: Buffer,
    contentType: string,
  ): Promise<void>;

  /** URL présignée de lecture (GET), à durée limitée. */
  abstract presignDownload(key: string): Promise<string>;

  /**
   * Supprime définitivement l'objet [key] du stockage (effacement RGPD —
   * NODE-118). Idempotent : ne lève pas si l'objet est déjà absent.
   */
  abstract delete(key: string): Promise<void>;

  /** Inventaire utilisé par le ramasse-miettes d'objets orphelins. */
  abstract list(prefix?: string): Promise<StoredObject[]>;
}
