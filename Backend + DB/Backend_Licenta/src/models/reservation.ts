export interface Reservation {
  id: number;
  userId: number;
  vehicleId: number;
  parkingSpotId: number;
  startTime: Date;
  endTime: Date;
  status: 'pending' | 'active' | 'completed' | 'cancelled';
  totalPrice: number;
}
