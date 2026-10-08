import { Routes } from '@angular/router';
import { homeRedirect, roleGuard } from './core/guards';

export const routes: Routes = [
  { path: '', pathMatch: 'full', canActivate: [homeRedirect], children: [] },
  {
    path: 'book',
    title: 'Prendre rendez-vous · MediRDV',
    canActivate: [roleGuard('patient')],
    loadComponent: () => import('./pages/book/book').then((m) => m.BookPage),
  },
  {
    path: 'notifications',
    title: 'Mes notifications · MediRDV',
    canActivate: [roleGuard('patient')],
    loadComponent: () => import('./pages/notifications/notifications').then((m) => m.NotificationsPage),
  },
  {
    path: 'agenda',
    title: 'Agenda · MediRDV',
    canActivate: [roleGuard('practitioner', 'admin')],
    loadComponent: () => import('./pages/agenda/agenda').then((m) => m.AgendaPage),
  },
  {
    path: 'patients',
    title: 'Patients · MediRDV',
    canActivate: [roleGuard('practitioner', 'admin')],
    loadComponent: () => import('./pages/patients/patients').then((m) => m.PatientsPage),
  },
  { path: '**', redirectTo: '' },
];
