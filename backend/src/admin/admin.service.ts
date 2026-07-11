import { Injectable } from '@nestjs/common';
import { DataSource } from 'typeorm';
import { StorageService } from '../storage/storage.service';

const MONITORED_TABLES = [
  'users',
  'refresh_tokens',
  'password_reset_tokens',
  'email_verification_tokens',
  'flowers',
  'flower_photos',
  'albums',
  'flower_albums',
  'groups',
  'group_members',
  'album_permissions',
  'friendships',
  'shares',
  'flower_comments',
  'flower_likes',
  'notifications',
  'species',
  'species_proposals',
  'device_tokens',
] as const;

interface ImageRow {
  kind: 'flower' | 'photo' | 'avatar';
  id: string;
  ownerId: string;
  ownerName: string;
  imageKey: string;
  thumbnailKey: string | null;
  label: string | null;
  createdAt: Date;
}

@Injectable()
export class AdminService {
  constructor(
    private readonly dataSource: DataSource,
    private readonly storage: StorageService,
  ) {}

  async overview() {
    const tableEntries = await Promise.all(
      MONITORED_TABLES.map(async (table) => {
        // Les identifiants viennent exclusivement de la constante ci-dessus.
        const [row] = await this.dataSource.query(
          `SELECT COUNT(*)::int AS count FROM "${table}"`,
        );
        return [table, Number(row.count)] as const;
      }),
    );
    const [database] = await this.dataSource.query(`
      SELECT
        pg_database_size(current_database())::bigint AS "sizeBytes",
        (SELECT COUNT(*)::int FROM pg_stat_activity
          WHERE datname = current_database()) AS "connections"
    `);
    const recentUsers = await this.dataSource.query(`
      SELECT id, email, display_name AS "displayName",
        email_verified AS "emailVerified", created_at AS "createdAt"
      FROM users ORDER BY created_at DESC LIMIT 10
    `);
    const recentFlowers = await this.dataSource.query(`
      SELECT f.id, f.species, f.visibility, f.created_at AS "createdAt",
        f.needs_identification AS "needsIdentification",
        u.display_name AS "ownerName"
      FROM flowers f JOIN users u ON u.id = f.owner_id
      WHERE f.deleted_at IS NULL
      ORDER BY f.created_at DESC LIMIT 10
    `);

    return {
      generatedAt: new Date().toISOString(),
      uptimeSeconds: Math.floor(process.uptime()),
      database: {
        sizeBytes: Number(database.sizeBytes),
        connections: Number(database.connections),
      },
      tables: Object.fromEntries(tableEntries),
      recentUsers,
      recentFlowers,
    };
  }

  async images(requestedPage: number, requestedPageSize: number) {
    const page = Math.max(1, requestedPage || 1);
    const pageSize = Math.min(100, Math.max(1, requestedPageSize || 24));
    const offset = (page - 1) * pageSize;
    const imageCandidates = `
      SELECT 'flower' AS kind, f.id, f.owner_id AS "ownerId",
        u.display_name AS "ownerName", f.image_key AS "imageKey",
        f.thumbnail_key AS "thumbnailKey", f.species AS label,
        f.created_at AS "createdAt"
      FROM flowers f JOIN users u ON u.id = f.owner_id
      WHERE f.deleted_at IS NULL
      UNION ALL
      SELECT 'photo' AS kind, p.id, f.owner_id AS "ownerId",
        u.display_name AS "ownerName", p.image_key AS "imageKey",
        p.thumbnail_key AS "thumbnailKey", f.species AS label,
        p.created_at AS "createdAt"
      FROM flower_photos p
      JOIN flowers f ON f.id = p.flower_id
      JOIN users u ON u.id = f.owner_id
      WHERE f.deleted_at IS NULL
      UNION ALL
      SELECT 'avatar' AS kind, u.id, u.id AS "ownerId",
        u.display_name AS "ownerName", u.avatar_key AS "imageKey",
        NULL AS "thumbnailKey", 'Avatar' AS label,
        u.updated_at AS "createdAt"
      FROM users u WHERE u.avatar_key IS NOT NULL
    `;
    // La photo de couverture est référencée deux fois dans le modèle :
    // `flowers.image_key` et une ligne `flower_photos`. L'inventaire représente
    // les objets du stockage, donc une même clé ne doit produire qu'une carte.
    // On privilégie la fleur principale, plus parlante dans le dashboard.
    const imageUnion = `
      SELECT DISTINCT ON ("imageKey") *
      FROM (${imageCandidates}) candidates
      ORDER BY "imageKey",
        CASE kind WHEN 'flower' THEN 0 WHEN 'photo' THEN 1 ELSE 2 END,
        "createdAt" DESC
    `;
    const [countRow] = await this.dataSource.query(
      `SELECT COUNT(*)::int AS count FROM (${imageUnion}) images`,
    );
    const rows: ImageRow[] = await this.dataSource.query(
      `SELECT * FROM (${imageUnion}) images
       ORDER BY "createdAt" DESC LIMIT $1 OFFSET $2`,
      [pageSize, offset],
    );
    const items = await Promise.all(
      rows.map(async (row) => ({
        ...row,
        previewUrl: await this.safeUrl(row.thumbnailKey ?? row.imageKey),
        originalUrl: await this.safeUrl(row.imageKey),
      })),
    );
    const total = Number(countRow.count);
    return {
      page,
      pageSize,
      total,
      totalPages: Math.max(1, Math.ceil(total / pageSize)),
      items,
    };
  }

  private async safeUrl(key: string): Promise<string | null> {
    try {
      return await this.storage.presignDownload(key);
    } catch {
      return null;
    }
  }
}
