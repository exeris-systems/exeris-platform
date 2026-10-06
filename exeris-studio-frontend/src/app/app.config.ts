import { ApplicationConfig, provideZonelessChangeDetection } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';

import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZonelessChangeDetection(),
    // The generated detail and form components read their `id` as a component input, so route
    // parameters must bind to inputs.
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(),
  ],
};
