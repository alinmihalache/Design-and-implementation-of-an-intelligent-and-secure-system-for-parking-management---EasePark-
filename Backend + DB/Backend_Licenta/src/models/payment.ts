export interface Payment {
  id: number;
  reservationId: number;
  userId: number;
  amount: number;
  status: 'pending' | 'completed' | 'failed';
  paymentMethod: 'card' | 'cash';
  timestamp: Date;
}
