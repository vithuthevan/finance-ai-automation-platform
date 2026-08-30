import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';

@Component({
  standalone: true,
  imports: [RouterLink, MatButtonModule],
  template: `
    <div class="page">
      <h1>Unauthorized</h1>
      <p>Your role does not have access to that page.</p>
      <a mat-flat-button color="primary" routerLink="/app/dashboard">Return to workspace</a>
    </div>
  `
})
export class UnauthorizedPage {}
