// Middleware de exemplu pentru autentificare
import { Request, Response, NextFunction } from 'express';

export function authMiddleware(req: Request, res: Response, next: NextFunction) {
  // Exemplu simplu: permite orice request
  next();
}
