import { CloseActionLink } from './month-end.models';

export function navigateAction(router: { navigate: (c: unknown[]) => void }, action: CloseActionLink): void {
  router.navigate([action.path, action.query]);
}

export function stateBadgeStatus(state: string): string {
  switch (state) {
    case 'READY': return 'APPROVED';
    case 'ATTENTION': return 'PENDING';
    case 'BLOCKED': return 'VOID';
    case 'CLOSED': return 'CLOSED';
    default: return state;
  }
}

export function buildRouterLink(action: CloseActionLink): { path: string; queryParams: Record<string, string> } {
  return { path: action.path.replace(/^\//, ''), queryParams: action.query ?? {} };
}
