import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { DashboardComponent } from './dashboard.component';
import { AuthService } from '../../core/auth.service';

describe('DashboardComponent', () => {
  let fixture: ComponentFixture<DashboardComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [{
        provide: AuthService,
        useValue: {
          claims: signal(null),
          get isAuthenticated() { return false; }
        }
      }, provideRouter([])]
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();
  });

  it('renders the French welcome message', () => {
    expect(fixture.nativeElement.querySelector('h1').textContent).toContain('Prenez soin de vous');
  });

  it('toggles the page theme', () => {
    fixture.componentInstance.toggleTheme();
    expect(fixture.componentInstance.isDark()).toBeTrue();
    expect(document.documentElement.classList.contains('dark')).toBeTrue();
    document.documentElement.classList.remove('dark');
  });
});
