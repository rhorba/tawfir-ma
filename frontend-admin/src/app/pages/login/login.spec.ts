import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { By } from '@angular/platform-browser';
import { vi } from 'vitest';

import { Login } from './login';
import { AuthApiError, AuthService } from '../../core/auth.service';

function fakeJwt(role: string): string {
  const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const payload = btoa(JSON.stringify({ sub: 'user-1', role }));
  return `${header}.${payload}.signature`;
}

describe('Login', () => {
  let component: Login;
  let fixture: ComponentFixture<Login>;
  let authService: {
    requestOtp: ReturnType<typeof vi.fn>;
    verifyOtp: ReturnType<typeof vi.fn>;
    storeTokens: ReturnType<typeof vi.fn>;
    clearTokens: ReturnType<typeof vi.fn>;
    decodeRole: ReturnType<typeof vi.fn>;
  };
  let router: Router;

  beforeEach(async () => {
    authService = {
      requestOtp: vi.fn().mockResolvedValue(undefined),
      verifyOtp: vi.fn(),
      storeTokens: vi.fn(),
      clearTokens: vi.fn(),
      decodeRole: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [Login],
      providers: [provideRouter([]), { provide: AuthService, useValue: authService }],
    }).compileComponents();

    fixture = TestBed.createComponent(Login);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('starts on the phone step', () => {
    expect(component.step()).toBe('phone');
  });

  it('moves to the otp step after requesting a code', async () => {
    component.phoneNumber = '+212612345678';
    await component.requestOtp();
    expect(authService.requestOtp).toHaveBeenCalledWith('+212612345678');
    expect(component.step()).toBe('otp');
  });

  it('shows a rate-limit message when otp/request is throttled', async () => {
    authService.requestOtp.mockRejectedValueOnce(new AuthApiError(429, 'Too many requests'));
    component.phoneNumber = '+212612345678';
    await component.requestOtp();
    expect(component.error()).toMatch(/too many attempts/i);
    expect(component.step()).toBe('phone');
  });

  it('stores tokens and navigates to /dashboard for an ADMIN account', async () => {
    const tokens = { accessToken: fakeJwt('ADMIN'), refreshToken: 'r1', tokenType: 'Bearer', expiresInSeconds: 900 };
    authService.verifyOtp.mockResolvedValue(tokens);
    authService.decodeRole.mockReturnValue('ADMIN');
    component.code = '123456';

    await component.verifyOtp();

    expect(authService.storeTokens).toHaveBeenCalledWith(tokens);
    expect(router.navigateByUrl).toHaveBeenCalledWith('/dashboard');
    expect(component.error()).toBeNull();
  });

  it('rejects a non-ADMIN account locally without storing tokens or navigating', async () => {
    const tokens = { accessToken: fakeJwt('MEMBER'), refreshToken: 'r1', tokenType: 'Bearer', expiresInSeconds: 900 };
    authService.verifyOtp.mockResolvedValue(tokens);
    authService.decodeRole.mockReturnValue('MEMBER');
    component.code = '123456';

    await component.verifyOtp();

    expect(authService.storeTokens).not.toHaveBeenCalled();
    expect(router.navigateByUrl).not.toHaveBeenCalled();
    expect(component.error()).toMatch(/doesn't have admin access/i);
  });

  it('shows an error when the code is invalid', async () => {
    authService.verifyOtp.mockRejectedValue(new AuthApiError(401, 'Invalid or expired code'));
    component.code = '000000';

    await component.verifyOtp();

    expect(component.error()).toMatch(/invalid or expired code/i);
  });

  it('renders the phone form and submits it via the DOM', () => {
    fixture.detectChanges();
    const input = fixture.debugElement.query(By.css('input[name="phoneNumber"]')).nativeElement as HTMLInputElement;
    input.value = '+212612345678';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    const form = fixture.debugElement.query(By.css('form')).nativeElement as HTMLFormElement;
    expect(() => form.dispatchEvent(new Event('submit'))).not.toThrow();
  });

  it('renders the otp form after moving past the phone step', async () => {
    component.phoneNumber = '+212612345678';
    await component.requestOtp();
    fixture.detectChanges();

    const input = fixture.debugElement.query(By.css('input[name="code"]')).nativeElement as HTMLInputElement;
    input.value = '123456';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    const form = fixture.debugElement.query(By.css('form')).nativeElement as HTMLFormElement;
    expect(() => form.dispatchEvent(new Event('submit'))).not.toThrow();
  });
});
