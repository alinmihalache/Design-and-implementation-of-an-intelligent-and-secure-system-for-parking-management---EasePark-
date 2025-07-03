export interface Vehicle {
  id: number;
  userId: number;
  plateNumber: string;
  make: string;
  model: string;
  year: number;
  type: 'car' | 'motorcycle' | 'van';
}
