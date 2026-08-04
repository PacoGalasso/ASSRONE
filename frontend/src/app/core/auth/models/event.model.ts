export interface EventItem {
  id: number;
  title: string;
  description: string;
  type: string;
  eventDate: string;       // ISO yyyy-MM-dd
  startTime: string;       // HH:mm:ss
  endTime: string;
  location: string;
  maxParticipants: number;
  currentParticipants: number;
}
export interface CreateEventRequest {
  title: string;
  description: string;
  type: string;
  eventDate: string;
  startTime: string;
  endTime: string;
  location: string;
  maxParticipants: number;
}

export interface UpdateEventRequest {
  title: string;
  description: string;
  type: string;
  eventDate: string;
  startTime: string;
  endTime: string;
  location: string;
  maxParticipants: number;
}

export interface EventRegistrationRequest {
  fullName: string;
  email: string;
}
