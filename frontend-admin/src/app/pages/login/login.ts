import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

type Step = 'phone' | 'otp';

@Component({
  selector: 'app-login',
  imports: [FormsModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  step = signal<Step>('phone');
  phoneNumber = '';
  code = '';

  requestOtp(): void {
    // Wired to POST /api/v1/auth/otp/request in Epic 1 (story 1.1)
    this.step.set('otp');
  }

  verifyOtp(): void {
    // Wired to POST /api/v1/auth/otp/verify in Epic 1 (story 1.2/1.4 — admin TOTP)
  }
}
