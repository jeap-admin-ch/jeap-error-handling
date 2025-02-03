import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ErrorGroupPageComponent } from './error-group-page.component';

describe('ErrorGroupPageComponent', () => {
  let component: ErrorGroupPageComponent;
  let fixture: ComponentFixture<ErrorGroupPageComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ErrorGroupPageComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(ErrorGroupPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
