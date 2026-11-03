import { Component, input } from '@angular/core';

@Component({
  selector: 'app-icon',
  standalone: true,
  template: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path [attr.d]="paths[name()] || paths['spark']" /></svg>`,
  styles: [`:host{display:inline-flex;width:22px;height:22px;flex:none}svg{width:100%;height:100%}`]
})
export class IconComponent {
  readonly name = input('spark');
  readonly paths: Record<string, string> = {
    spark: 'm12 3 2.8 6.2L21 12l-6.2 2.8L12 21l-2.8-6.2L3 12l6.2-2.8L12 3Z',
    dashboard: 'M3 3h7v7H3V3Zm11 0h7v7h-7V3ZM3 14h7v7H3v-7Zm11 0h7v7h-7v-7Z',
    chat: 'M21 11.5a8.4 8.4 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.4 8.4 0 0 1-3.8-.9L3 21l1.9-5.7a8.4 8.4 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.4 8.4 0 0 1 3.8-.9h.5a8.5 8.5 0 0 1 8 8v.5Z',
    agents: 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2m20 0v-2a4 4 0 0 0-3-3.9M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm8-7.9a4 4 0 0 1 0 7.8',
    folder: 'M3 7V5a2 2 0 0 1 2-2h5l2 3h7a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7Z',
    settings: 'M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8Zm8 4a7.8 7.8 0 0 0-.2-1.8l1.2-1.1-2-3.4-1.5.5a8 8 0 0 0-3.1-1.8L14 3h-4l-.4 1.4a8 8 0 0 0-3.1 1.8L5 5.7 3 9.1l1.2 1.1a8 8 0 0 0 0 3.6L3 14.9l2 3.4 1.5-.5a8 8 0 0 0 3.1 1.8L10 21h4l.4-1.4a8 8 0 0 0 3.1-1.8l1.5.5 2-3.4-1.2-1.1A7.8 7.8 0 0 0 20 12Z',
    arrow: 'M4 12h16m-6-6 6 6-6 6',
    send: 'M12 20V4m-6 6 6-6 6 6',
    code: 'm8 7-5 5 5 5m8-10 5 5-5 5m-3-14-2 18',
    language: 'M3 5h12M9 3v2m4 0c-1 7-5 10-10 12m2-9c2 5 5 8 9 10m1-1h7m-9 4 5-13 5 13',
    mail: 'M4 5h16a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1Zm-1 1 9 7 9-7',
    document: 'M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8l-6-6Zm0 0v6h6M8 13h8m-8 4h6',
    plus: 'M12 5v14M5 12h14',
    check: 'm5 12 4 4L19 6',
    menu: 'M4 6h16M4 12h16M4 18h16',
    close: 'm6 6 12 12M6 18 18 6',
    shield: 'M12 3 3 7v5c0 5 9 9 9 9s9-4 9-9V7l-9-4Zm-4 9 3 3 5-6',
    bolt: 'm13 2-9 12h7l-1 8 10-13h-8l1-7Z',
    globe: 'M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0ZM3 12h18m-9-9a16 16 0 0 0 0 18 16 16 0 0 0 0-18Z',
    trash: 'M3 6h18M9 6V3h6v3M5 6l1 15h12l1-15M10 10v7m4-7v7'
  };
}
