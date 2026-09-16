import { defineConfig } from 'tsup';

export default defineConfig({
  entry: ['src/index.ts'],
  format: ['cjs'],
  target: 'node20',
  dts: true,
  clean: true,
  splitting: false,
  noExternal: ['commander', 'axios'],
  esbuildOptions: (options) => {
    options.resolveExtensions = ['.ts', '.tsx', '.js', '.jsx', '.json', '.node'];
  },
});