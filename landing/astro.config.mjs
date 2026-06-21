import { defineConfig } from 'astro/config';

export default defineConfig({
  output: 'static',
  site: 'https://florapin.pattounecorp.ovh',
  build: {
    assets: '_assets',
  },
});
