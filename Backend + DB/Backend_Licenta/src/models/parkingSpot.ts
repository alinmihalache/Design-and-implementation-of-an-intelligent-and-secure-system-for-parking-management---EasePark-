export interface ParkingSpot {
  id: number;
  latitude: number;
  longitude: number;
  address: string;
  isOccupied: boolean;
  pricePerHour: number;
  type: 'standard' | 'handicap' | 'electric';
}
