import { Component, EventEmitter, Input, Output } from '@angular/core';

import { FormsModule } from '@angular/forms';

import { RouterLink, RouterLinkActive } from '@angular/router';

import { MatFormFieldModule } from '@angular/material/form-field';

import { MatSelectModule } from '@angular/material/select';

import { MatInputModule } from '@angular/material/input';

import { MatButtonModule } from '@angular/material/button';

import { MatDatepickerModule } from '@angular/material/datepicker';

import { ReportContextService } from './report-context.service';

import {

  COMPARE_DATE_RANGE_ERROR,

  DATE_RANGE_ERROR,

  formatIsoDate,

  isInvalidDateRange,

  parseIsoDate

} from '../../shared/date.util';
import { LoadingStateComponent } from '../../shared/ui/loading-state.component';



@Component({

  standalone: true,

  selector: 'app-report-filters',

  imports: [

    FormsModule, RouterLink, RouterLinkActive, MatFormFieldModule, MatSelectModule,

    MatInputModule, MatButtonModule, MatDatepickerModule, LoadingStateComponent

  ],

  template: `

    <nav class="report-nav">

      <a routerLink="/app/reports" routerLinkActive="active" [routerLinkActiveOptions]="{exact: true}">Profit &amp; Loss</a>

      <a routerLink="/app/reports/income" routerLinkActive="active">Income</a>

      <a routerLink="/app/reports/expenses" routerLinkActive="active">Expenses</a>

      <a routerLink="/app/reports/trends" routerLinkActive="active">Trends</a>

    </nav>

    <div class="filter-bar">

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

        <input matInput [matDatepicker]="fromPicker" [ngModel]="toDate(ctx.from())" (ngModelChange)="ctx.from.set(fromDate($event))">

        <mat-datepicker-toggle matIconSuffix [for]="fromPicker"></mat-datepicker-toggle>

        <mat-datepicker #fromPicker></mat-datepicker>

      </mat-form-field>

      <mat-form-field>

        <mat-label>To</mat-label>

        <input matInput [matDatepicker]="toPicker" [ngModel]="toDate(ctx.to())" (ngModelChange)="ctx.to.set(fromDate($event))">

        <mat-datepicker-toggle matIconSuffix [for]="toPicker"></mat-datepicker-toggle>

        <mat-datepicker #toPicker></mat-datepicker>

      </mat-form-field>

      @if (showCompare) {

        <mat-form-field>

          <mat-label>Compare from</mat-label>

          <input matInput [matDatepicker]="cFromPicker" [ngModel]="toDate(ctx.compareFrom())" (ngModelChange)="ctx.compareFrom.set(fromDate($event))">

          <mat-datepicker-toggle matIconSuffix [for]="cFromPicker"></mat-datepicker-toggle>

          <mat-datepicker #cFromPicker></mat-datepicker>

        </mat-form-field>

        <mat-form-field>

          <mat-label>Compare to</mat-label>

          <input matInput [matDatepicker]="cToPicker" [ngModel]="toDate(ctx.compareTo())" (ngModelChange)="ctx.compareTo.set(fromDate($event))">

          <mat-datepicker-toggle matIconSuffix [for]="cToPicker"></mat-datepicker-toggle>

          <mat-datepicker #cToPicker></mat-datepicker>

        </mat-form-field>

      }

      <div class="filter-actions">

        <button mat-flat-button color="primary" (click)="onRun()" [disabled]="!!dateRangeError() || loading">Run</button>

        @if (canExport) {

          <button mat-stroked-button (click)="export.emit('csv')" [disabled]="exporting || !!dateRangeError()">CSV</button>

          <button mat-stroked-button (click)="export.emit('xlsx')" [disabled]="exporting || !!dateRangeError()">Excel</button>

        }

      </div>

    </div>

    @if (dateRangeError()) {

      <p class="field-error">{{ dateRangeError() }}</p>

    }

    @if (message) { <p class="hint">{{ message }}</p> }

    @if (loading) { <app-loading-state message="Loading report…" /> }

    @if (ctx.clientsError()) { <p class="field-error">{{ ctx.clientsError() }}</p> }

  `

})

export class ReportFiltersComponent {

  @Input() showCompare = false;

  @Input() canExport = true;

  @Input() exporting = false;

  @Input() loading = false;

  @Input() message = '';

  @Output() run = new EventEmitter<void>();

  @Output() export = new EventEmitter<string>();



  constructor(readonly ctx: ReportContextService) {}



  dateRangeError(): string | null {

    if (isInvalidDateRange(this.ctx.from(), this.ctx.to())) {

      return DATE_RANGE_ERROR;

    }

    if (this.showCompare && isInvalidDateRange(this.ctx.compareFrom(), this.ctx.compareTo())) {

      return COMPARE_DATE_RANGE_ERROR;

    }

    return null;

  }



  onRun(): void {

    if (this.dateRangeError()) {

      return;

    }

    this.run.emit();

  }



  toDate(value: string): Date | null {

    return parseIsoDate(value);

  }



  fromDate(value: Date | null): string {

    return formatIsoDate(value);

  }

}


