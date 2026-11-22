import { CopyObjectCommand, DeleteObjectCommand, GetObjectCommand, HeadBucketCommand, PutObjectCommand, S3Client } from '@aws-sdk/client-s3';
import { getSignedUrl } from '@aws-sdk/s3-request-presigner';
import { loadConfig } from '../config.js';
import { runtimeMetrics } from '../metrics.js';

export interface ArtifactStorage {
  putQuarantine(key: string, bytes: Buffer, sha256: string): Promise<void>;
  readQuarantine(key: string): Promise<Buffer>;
  publish(key: string, targetKey: string): Promise<void>;
  readPublished(key: string): Promise<Buffer>;
  deleteQuarantine(key: string): Promise<void>;
  quarantineReviewUrl(key: string): Promise<string>;
  health(signal?: AbortSignal): Promise<void>;
}
export class S3ArtifactStorage implements ArtifactStorage {
  private readonly client: S3Client;
  constructor(private readonly config = loadConfig()) {
    this.client = new S3Client({ region: config.S3_REGION, endpoint: config.S3_ENDPOINT,
      forcePathStyle: config.S3_FORCE_PATH_STYLE, credentials: { accessKeyId: config.S3_ACCESS_KEY_ID, secretAccessKey: config.S3_SECRET_ACCESS_KEY } });
  }
  async putQuarantine(key: string, bytes: Buffer, sha256: string) {
    await this.track(() => this.client.send(new PutObjectCommand({ Bucket: this.config.S3_QUARANTINE_BUCKET, Key: key, Body: bytes, ContentLength: bytes.length,
      ContentType: 'application/octet-stream', Metadata: { sha256 } })));
  }
  async readQuarantine(key: string): Promise<Buffer> {
    const result = await this.track(() => this.client.send(new GetObjectCommand({ Bucket: this.config.S3_QUARANTINE_BUCKET, Key: key })));
    if (!result.Body) throw new Error('Quarantine artifact is unavailable.');
    return Buffer.from(await result.Body.transformToByteArray());
  }
  async publish(key: string, targetKey: string) {
    await this.track(() => this.client.send(new CopyObjectCommand({ Bucket: this.config.S3_PUBLISHED_BUCKET, Key: targetKey,
      CopySource: `${this.config.S3_QUARANTINE_BUCKET}/${encodeURIComponent(key).replaceAll('%2F','/')}`, MetadataDirective: 'COPY' })));
  }
  async readPublished(key: string): Promise<Buffer> {
    const result=await this.track(() => this.client.send(new GetObjectCommand({Bucket:this.config.S3_PUBLISHED_BUCKET,Key:key})));
    if(!result.Body)throw new Error('Published artifact is unavailable.');
    return Buffer.from(await result.Body.transformToByteArray());
  }
  async deleteQuarantine(key: string) { await this.track(() => this.client.send(new DeleteObjectCommand({ Bucket: this.config.S3_QUARANTINE_BUCKET, Key: key }))); }
  async quarantineReviewUrl(key: string) { return this.track(() => getSignedUrl(this.client, new GetObjectCommand({ Bucket: this.config.S3_QUARANTINE_BUCKET, Key: key }), { expiresIn:300 })); }
  async health(signal?: AbortSignal) {
    await this.track(() => Promise.all([
      this.client.send(new HeadBucketCommand({ Bucket: this.config.S3_QUARANTINE_BUCKET }), { abortSignal: signal }),
      this.client.send(new HeadBucketCommand({ Bucket: this.config.S3_PUBLISHED_BUCKET }), { abortSignal: signal }),
    ]));
  }
  private async track<T>(operation: () => Promise<T>): Promise<T> {
    try { return await operation(); }
    catch (error) { runtimeMetrics.increment('doom_storage_operation_failures_total'); throw error; }
  }
}
export class MemoryArtifactStorage implements ArtifactStorage {
  readonly quarantine = new Map<string, Buffer>(); readonly published = new Map<string, Buffer>();
  async putQuarantine(key: string, bytes: Buffer) { this.quarantine.set(key, Buffer.from(bytes)); }
  async readQuarantine(key: string) { const value = this.quarantine.get(key); if (!value) throw new Error('Quarantine artifact is unavailable.'); return Buffer.from(value); }
  async publish(key: string, targetKey: string) { const bytes = await this.readQuarantine(key); this.published.set(targetKey, bytes); }
  async readPublished(key: string) { const value=this.published.get(key);if(!value)throw new Error('Published artifact is unavailable.');return Buffer.from(value); }
  async deleteQuarantine(key: string) { this.quarantine.delete(key); }
  async quarantineReviewUrl(key: string) { if(!this.quarantine.has(key))throw new Error('Quarantine artifact is unavailable.');return `memory://quarantine/${key}`; }
  async health() {}
}
