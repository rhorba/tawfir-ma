import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { AuthApiError, AuthService } from '../../core/auth.service';

type Step = 'phone' | 'otp';

@Component({
  selector: 'app-login',
  imports: [FormsModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  step = signal<Step>('phone');
  error = signal<string | null>(null);
  isSubmitting = signal(false);
  phoneNumber = '';
  code = '';

  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  async requestOtp(): Promise<void> {
    this.error.set(null);
    this.isSubmitting.set(true);
    try {
      await this.authService.requestOtp(this.phoneNumber);
      this.step.set('otp');
    } catch (err) {
      this.error.set(this.toMessage(err, 'Could not send the code. Please try again.'));
    } finally {
      this.isSubmitting.set(false);
    }
  }

  async verifyOtp(): Promise<void> {
    this.error.set(null);
    this.isSubmitting.set(true);
    try {
      const tokens = await this.authService.verifyOtp(this.phoneNumber, this.code);
      const role = this.authService.decodeRole(tokens.accessToken);
      if (role !== 'ADMIN') {
        this.error.set("This account doesn't have admin access.");
        return;
      }
      this.authService.storeTokens(tokens);
      await this.router.navigateByUrl('/dashboard');
    } catch (err) {
      this.error.set(this.toMessage(err, 'Could not verify the code. Please try again.'));
    } finally {
      this.isSubmitting.set(false);
    }
  }

  private toMessage(err: unknown, fallback: string): string {
    if (err instanceof AuthApiError && err.status === 429) {
      return 'Too many attempts. Please wait a few minutes and try again.';
    }
    if (err instanceof AuthApiError && err.message) {
      return err.message;
    }
    return fallback;
  }
}
