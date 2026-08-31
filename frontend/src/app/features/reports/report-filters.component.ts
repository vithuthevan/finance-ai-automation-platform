import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { ReportContextService } from './report-context.service';

@Component({
  standalone: true,
  selector: 'app-report-filters',
  imports: [FormsModule, RouterLink, RouterLinkActive, MatFormFieldModule, MatSelectModule, MatInputModule, MatButtonModule],
  template: `
    <nav class="report-nav">
      <a routerLink="/app/reports" routerLinkActive="active" [routerLinkActiveOptions]="{exact: true}">Profit &amp; Loss</a>
      <a routerLink="/app/reports/income" routerLinkActive="active">Income</a>
      <a routerLink="/app/reports/expenses" routerLinkActive="active">Expenses</a>
      <a routerLink="/app/reports/trends" routerLinkActive="active">Trends</a>
    </nav>
    <div class="toolbar-row">
      @if (!ctx.lockClient()) {
        <mat-form-field>
          <mat-label>Client</mat-label>
          <mat-select [ngModel]="ctx.clientId()" (ngModelChange)="ctx.clientId.set($event)">
            @for (c of ctx.clients(); track c.id) { <mat-option [value]="c.id">{{ c.name }}</mat-option> }
          </mat-select>
        </mat-form-field>
      }
      <mat-form-field>
        <mat-label>From</mat-label>
        <input matInput type="date" [ngModel]="ctx.from()" (ngModelChange)="ctx.from.set($event)">
      </mat-form-field>
      <mat-form-field>
        <mat-label>To</mat-label>
        <input matInput type="date" [ngModel]="ctx.to()" (ngModelChange)="ctx.to.set($event)">
      </mat-form-field>
      @if (showCompare) {
        <mat-form-field>
          <mat-label>Compare from</mat-label>
          <input matInput type="date" [ngModel]="ctx.compareFrom()" (ngModelChange)="ctx.compareFrom.set($event)">
        </mat-form-field>
        <mat-form-field>
          <mat-label>Compare to</mat-label>
          <input matInput type="date" [ngModel]="ctx.compareTo()" (ngModelChange)="ctx.compareTo.set($event)">
        </mat-form-field>
      }
      <button mat-flat-button color="primary" (click)="run.emit()">Run</button>
      @if (canExport) {
        <button mat-stroked-button (click)="export.emit('csv')" [disabled]="exporting">CSV</button>
        <button mat-stroked-button (click)="export.emit('xlsx')" [disabled]="exporting">Excel</button>
      }
    </div>
    @if (message) { <p class="hint">{{ message }}</p> }
  `
})
export class ReportFiltersComponent {
  @Input() showCompare = false;
  @Input() canExport = true;
  @Input() exporting = false;
  @Input() message = '';
  @Output() run = new EventEmitter<void>();
  @Output() export = new EventEmitter<string>();

  constructor(readonly ctx: ReportContextService) {}
}
