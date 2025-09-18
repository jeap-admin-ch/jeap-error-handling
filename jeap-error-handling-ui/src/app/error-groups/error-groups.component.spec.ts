import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ErrorGroupsComponent } from './error-groups.component';

describe('ErrorGroupsComponent', () => {
  let component: ErrorGroupsComponent;
  let fixture: ComponentFixture<ErrorGroupsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ErrorGroupsComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(ErrorGroupsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
