// features/membership/membership-application.model.ts
export type MembershipType = 'INDIVIDUEL_ACTIF' | 'INDIVIDUEL_SOUTIEN' | 'COLLECTIF_ACTIF' | 'COLLECTIF_SOUTIEN';

export interface CreateMembershipApplicationRequest {
  fullName: string;
  email: string;
  phone?: string;
  membershipType: MembershipType;
  message?: string;
  charterAccepted: boolean;
}

export interface MembershipApplicationDto {
  id: number;
  fullName: string;
  email: string;
  phone?: string;
  membershipType: MembershipType;
  message?: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  submittedAt: string;
}
