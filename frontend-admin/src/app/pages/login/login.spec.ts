import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';

import { Login } from './login';

describe('Login', () => {
  let component: Login;
  let fixture: ComponentFixture<Login>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Login],
    }).compileComponents();

    fixture = TestBed.createComponent(Login);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('starts on the phone step', () => {
    expect(component.step()).toBe('phone');
  });

  it('moves to the otp step after requesting a code', () => {
    component.phoneNumber = '+212612345678';
    component.requestOtp();
    expect(component.step()).toBe('otp');
  });

  it('verifyOtp does not throw', () => {
    component.code = '123456';
    expect(() => component.verifyOtp()).not.toThrow();
  });

  it('renders the phone form and submits it via the DOM', () => {
    fixture.detectChanges();
    const input = fixture.debugElement.query(By.css('input[name="phoneNumber"]')).nativeElement as HTMLInputElement;
    input.value = '+212612345678';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    const form = fixture.debugElement.query(By.css('form')).nativeElement as HTMLFormElement;
    form.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(component.step()).toBe('otp');
  });

  it('renders the otp form and submits it via the DOM', () => {
    component.phoneNumber = '+212612345678';
    component.requestOtp();
    fixture.detectChanges();

    const input = fixture.debugElement.query(By.css('input[name="code"]')).nativeElement as HTMLInputElement;
    input.value = '123456';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    const form = fixture.debugElement.query(By.css('form')).nativeElement as HTMLFormElement;
    expect(() => form.dispatchEvent(new Event('submit'))).not.toThrow();
  });
});
