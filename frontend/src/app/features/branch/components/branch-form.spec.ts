import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
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
      providers: [provideHttpClient(), provideHttpClientTesting(), provideNoopAnimations()]
    });
    fixture = TestBed.createComponent(BranchForm);
    fixture.componentRef.setInput('mode', 'create');
    form = fixture.componentInstance;
    fixture.detectChanges();

    group().patchValue({
      branchCode: 'LATUR', branchName: 'Latur', branchType: 'BRANCH'
    });
  });

  it('omits the block entirely when the user section is untouched', () => {
    expect(emitted().branchUser).toBeNull();
  });

  it('sends what was typed, trimmed', () => {
    group().patchValue({
      branchUser: { firstName: ' Asha ', lastName: '', password: ' Str0ng#Pass1 ' }
    });

    expect(emitted().branchUser).toEqual({
      email: null, firstName: 'Asha', lastName: null, mobile: null, password: 'Str0ng#Pass1'
    });
  });

  it('login email and mobile are always the Contact details', () => {
    group().patchValue({ email: ' latur@legacy.test ', mobile: '+91 90000 00000' });

    const block = emitted().branchUser!;
    expect(block.email).toBe('latur@legacy.test');
    expect(block.mobile).toBe('+91 90000 00000');
    expect(block.password).toBeNull();
  });

  it('refuses to submit an invalid contact email', () => {
    group().patchValue({ email: 'not-an-address' });

    let called = false;
    form.saved.subscribe(() => (called = true));
    (form as unknown as { submit: () => void }).submit();

    expect(called).toBe(false);
  });
});
