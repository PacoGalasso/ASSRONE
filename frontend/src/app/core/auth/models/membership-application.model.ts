// features/membership/membership-application.model.ts
export type MembershipType = 'INDIVIDUEL' | 'COLLECTIF';

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
