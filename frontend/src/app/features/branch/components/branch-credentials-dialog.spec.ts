import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { BranchUserResponse } from '@core/models/branch.model';
import { BranchCredentialsDialog, BranchCredentialsData } from './branch-credentials-dialog';

/**
 * The role line on the once-only credentials dialog.
 *
 * <p>It used to be the literal string "Branch Manager", which was true only because the
 * backend happened to grant that role. Now the server says which company role it granted,
 * and the dialog repeats the server's answer — so a company that renames the role sees its
 * own name, and the screen cannot quietly disagree with `user_company_roles`.
 */
describe('BranchCredentialsDialog — the granted role', () => {
  const open = (user: Partial<BranchUserResponse>): BranchCredentialsDialog => {
    const data: BranchCredentialsData = {
      branchCode: 'PUNE_MAIN',
      branchName: 'Pune Main',
      user: {
        userId: 'u-1',
        email: 'pune-main@legacy-co.local',
        temporaryPassword: 'Gp7#tKm2Xq9wZa',
        assignedAsManager: true,
        ...user
      }
    };
    TestBed.configureTestingModule({
      providers: [
        provideNoopAnimations(),
        { provide: MAT_DIALOG_DATA, useValue: data },
        { provide: MatDialogRef, useValue: { close: () => undefined } }
      ]
    });
    return TestBed.createComponent(BranchCredentialsDialog).componentInstance;
  };

  beforeEach(() => TestBed.resetTestingModule());

  it('prints the role code the server granted, humanised', () => {
    expect(open({ roleCode: 'BRANCH_MANAGER' }).roleLabel()).toBe('Branch Manager');
  });

  it('follows a renamed role rather than asserting one of its own', () => {
    expect(open({ roleCode: 'AREA_INCHARGE' }).roleLabel()).toBe('Area Incharge');
  });

  it('falls back when the response carries no role', () => {
    // An older backend, or a create that predates the role wiring. Showing nothing at all
    // would read as "this account has no role", which is worse than the default.
    expect(open({ roleCode: null }).roleLabel()).toBe('Branch Manager');
  });
});
