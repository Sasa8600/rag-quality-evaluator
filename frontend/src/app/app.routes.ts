import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
  {
    path: 'dashboard',
    loadComponent: () => import('./features/dashboard/dashboard.component').then(m => m.DashboardComponent),
  },
  {
    path: 'documents',
    loadComponent: () => import('./features/documents/documents.component').then(m => m.DocumentsComponent),
  },
  {
    path: 'query',
    loadComponent: () => import('./features/query/query.component').then(m => m.QueryComponent),
  },
  {
    path: 'evaluation',
    loadComponent: () => import('./features/evaluation/evaluation.component').then(m => m.EvaluationComponent),
  },
  { path: '**', redirectTo: 'dashboard' },
];
