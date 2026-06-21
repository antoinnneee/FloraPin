import { defineConfig } from 'astro/config';

export default defineConfig({
  output: 'static',
  site: 'http://pattounecorp.ovh:8975',
  build: {
    assets: '_assets',
  },
});
