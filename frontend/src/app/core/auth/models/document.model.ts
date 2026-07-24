// features/members-portal/document.model.ts
export interface DocumentItem {
  id: number;
  title: string;
  description: string;
  originalFilename: string;
  contentType: string;
  fileSize: number;
  uploadedBy: string;
  uploadedAt: string;
}
