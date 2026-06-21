import { defineConfig } from 'astro/config';

export default defineConfig({
  output: 'static',
  site: 'https://florapin.fr',
  build: {
    assets: '_assets',
  },
});
