import { Component, EventEmitter, Input, Output } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';

@Component({
  selector: 'app-file-dropzone',
  standalone: true,
  imports: [MatIconModule, MatButtonModule],
  template: `
    <div
      class="dropzone"
      [class.disabled]="disabled"
      [class.dragging]="dragging"
      (dragover)="onDragOver($event)"
      (dragleave)="onDragLeave($event)"
      (drop)="onDrop($event)"
      (click)="fileInput.click()"
      role="button"
      tabindex="0"
      (keydown.enter)="fileInput.click()"
      (keydown.space)="$event.preventDefault(); fileInput.click()"
    >
      <mat-icon>cloud_upload</mat-icon>
      <div class="copy">
        <strong>{{ fileName || label }}</strong>
        <span>{{ hint }}</span>
      </div>
      <button mat-stroked-button type="button" [disabled]="disabled" (click)="$event.stopPropagation(); fileInput.click()">
        Choose file
      </button>
      <input #fileInput type="file" hidden [accept]="accept" [disabled]="disabled" (change)="onFileInput($event)">
    </div>
  `,
  styles: [`
    .dropzone {
      display: flex;
      align-items: center;
      gap: 14px;
      padding: 16px 18px;
      border: 1.5px dashed var(--fp-line);
      border-radius: var(--fp-radius);
      background: #fafbfc;
      cursor: pointer;
      transition: border-color 0.15s ease, background 0.15s ease;
    }
    .dropzone:hover, .dropzone.dragging {
      border-color: var(--fp-orange);
      background: var(--fp-orange-soft);
    }
    .dropzone.disabled {
      opacity: 0.55;
      pointer-events: none;
    }
    .dropzone mat-icon {
      color: var(--fp-orange);
    }
    .copy {
      display: flex;
      flex-direction: column;
      gap: 2px;
      flex: 1;
      min-width: 0;
    }
    .copy strong {
      font-size: 14px;
      color: var(--fp-ink);
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .copy span {
      font-size: 12px;
      color: var(--fp-muted);
    }
  `]
})
export class FileDropzoneComponent {
  @Input() accept = '';
  @Input() disabled = false;
  @Input() label = 'Drop a file here or browse';
  @Input() hint = 'PDF, images, or CSV';
  @Input() fileName = '';
  @Output() fileSelected = new EventEmitter<File>();

  dragging = false;

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    if (!this.disabled) {
      this.dragging = true;
    }
  }

  onDragLeave(event: DragEvent): void {
    event.preventDefault();
    this.dragging = false;
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragging = false;
    if (this.disabled) {
      return;
    }
    const file = event.dataTransfer?.files?.[0];
    if (file) {
      this.emit(file);
    }
  }

  onFileInput(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (file) {
      this.emit(file);
    }
    (event.target as HTMLInputElement).value = '';
  }

  private emit(file: File): void {
    this.fileName = file.name;
    this.fileSelected.emit(file);
  }
}
