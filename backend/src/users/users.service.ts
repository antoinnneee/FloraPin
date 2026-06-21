import { Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { User } from './user.entity';

/** Accès et création des utilisateurs. */
@Injectable()
export class UsersService {
  constructor(
    @InjectRepository(User)
    private readonly users: Repository<User>,
  ) {}

  /** Normalise un email (trim + minuscules). */
  static normalizeEmail(email: string): string {
    return email.trim().toLowerCase();
  }

  findByEmail(email: string): Promise<User | null> {
    return this.users.findOne({
      where: { email: UsersService.normalizeEmail(email) },
    });
  }

  findById(id: string): Promise<User | null> {
    return this.users.findOne({ where: { id } });
  }

  create(params: {
    email: string;
    passwordHash: string;
    displayName: string;
  }): Promise<User> {
    const user = this.users.create({
      email: UsersService.normalizeEmail(params.email),
      passwordHash: params.passwordHash,
      displayName: params.displayName,
    });
    return this.users.save(user);
  }
}
