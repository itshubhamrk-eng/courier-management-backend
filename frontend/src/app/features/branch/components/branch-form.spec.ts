import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { CreateBranchRequest } from '@core/models/branch.model';
import { BranchForm } from './branch-form';

/**
 * The create payload is what matters here: one submit has to carry the branch *and* its
 * user, and an untouched user section has to serialise as an absent block rather than an
 * object full of nulls — the two mean different things to a reader, even though the
 * backend treats them alike.
 */
describe('BranchForm — the branchUser block', () => {
  let fixture: ComponentFixture<BranchForm>;
  let form: BranchForm;

  const emitted = (): CreateBranchRequest => {
    let body: CreateBranchRequest | undefined;
    fixture.componentInstance.saved.subscribe((b) => (body = b as CreateBranchRequest));
    (form as unknown as { submit: () => void }).submit();
    expect(body).toBeDefined();
    return body!;
  };

  const group = () => (form as unknown as { form: { patchValue: (v: object) => void } }).form;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [BranchForm],
      providers: [provideNoopAnimations()]
    });
    fixture = TestBed.createComponent(BranchForm);
    fixture.componentRef.setInput('mode', 'create');
    form = fixture.componentInstance;
    fixture.detectChanges();

    group().patchValue({
      branchCode: 'LATUR', branchName: 'Latur', branchType: 'BOOKING_BRANCH'
    });
  });

  it('omits the block entirely when the user section is untouched', () => {
    expect(emitted().branchUser).toBeNull();
  });

  it('sends what was typed, trimmed', () => {
    group().patchValue({
      branchUser: {
        email: '  latur@legacy.test ', firstName: ' Asha ', lastName: '', mobile: '',
        password: ' Str0ng#Pass1 '
      }
    });

    expect(emitted().branchUser).toEqual({
      email: 'latur@legacy.test', firstName: 'Asha', lastName: null, mobile: null,
      password: 'Str0ng#Pass1'
    });
  });

  it('sends a partial block — one filled field is still a choice', () => {
    group().patchValue({ branchUser: { email: 'latur@legacy.test' } });

    const block = emitted().branchUser!;
    expect(block.email).toBe('latur@legacy.test');
    expect(block.password).toBeNull();
  });

  it('refuses to submit an invalid login address', () => {
    group().patchValue({ branchUser: { email: 'not-an-address' } });

    let called = false;
    form.saved.subscribe(() => (called = true));
    (form as unknown as { submit: () => void }).submit();

    expect(called).toBe(false);
  });
});
