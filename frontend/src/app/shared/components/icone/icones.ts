export interface Forma {
  tipo: 'path' | 'rect' | 'circle';
  d?: string;
  x?: number;
  y?: number;
  width?: number;
  height?: number;
  rx?: number;
  cx?: number;
  cy?: number;
  r?: number;
}

const path = (d: string): Forma => ({ tipo: 'path', d });
const rect = (x: number, y: number, width: number, height: number, rx = 1): Forma => ({
  tipo: 'rect',
  x,
  y,
  width,
  height,
  rx,
});
const circle = (cx: number, cy: number, r: number): Forma => ({ tipo: 'circle', cx, cy, r });

export const ICONES = {
  banco: [
    path('M11.12 2.198a2 2 0 0 1 1.76.006l7.866 3.847c.476.233.31.949-.22.949H3.474c-.53 0-.695-.716-.22-.949z'),
    path('M6 18v-7'),
    path('M10 18v-7'),
    path('M14 18v-7'),
    path('M18 18v-7'),
    path('M3 22h18'),
  ],
  painel: [rect(3, 3, 7, 9), rect(14, 3, 7, 5), rect(14, 12, 7, 9), rect(3, 16, 7, 5)],
  deposito: [path('M12 17V3'), path('m6 11 6 6 6-6'), path('M19 21H5')],
  saque: [path('m18 9-6-6-6 6'), path('M12 3v14'), path('M5 21h14')],
  transferencia: [path('M8 3 4 7l4 4'), path('M4 7h16'), path('m16 21 4-4-4-4'), path('M20 17H4')],
  extrato: [
    path('M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z'),
    path('M14 2v4a2 2 0 0 0 2 2h4'),
    path('M10 9H8'),
    path('M16 13H8'),
    path('M16 17H8'),
  ],
  clientes: [
    path('M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2'),
    circle(9, 7, 4),
    path('M22 21v-2a4 4 0 0 0-3-3.87'),
    path('M16 3.13a4 4 0 0 1 0 7.75'),
  ],
  gerentes: [path('M16 20V4a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16'), rect(2, 6, 20, 14, 2)],
  relatorio: [
    rect(8, 2, 8, 4),
    path('M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2'),
    path('M12 11h4'),
    path('M12 16h4'),
    path('M8 11h.01'),
    path('M8 16h.01'),
  ],
  carteira: [
    path('M19 7V4a1 1 0 0 0-1-1H5a2 2 0 0 0 0 4h15a1 1 0 0 1 1 1v4h-3a2 2 0 0 0 0 4h3a1 1 0 0 0 1-1v-2a1 1 0 0 0-1-1'),
    path('M3 5v14a2 2 0 0 0 2 2h15a1 1 0 0 0 1-1v-4'),
  ],
  sair: [path('M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4'), path('m16 17 5-5-5-5'), path('M21 12H9')],
} satisfies Record<string, Forma[]>;

export type NomeIcone = keyof typeof ICONES;
