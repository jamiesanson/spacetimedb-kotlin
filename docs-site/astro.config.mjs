// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';

// https://astro.build/config
export default defineConfig({
	site: 'https://jamiesanson.github.io',
	base: '/spacetimedb-kotlin',
	redirects: {
		'/api': '/api/index.html',
		'/api/': '/api/index.html',
	},
	integrations: [
		starlight({
			title: 'SpacetimeDB Kotlin SDK',
			social: [{ icon: 'github', label: 'GitHub', href: 'https://github.com/jamiesanson/spacetimedb-kotlin' }],
			sidebar: [
				{ label: 'Getting Started', slug: 'getting-started' },
				{
					label: 'Guides',
					items: [
						{ label: 'Generated Code', slug: 'guides/codegen' },
						{ label: 'Gradle Plugin', slug: 'guides/gradle-plugin' },
					],
				},
				{
					label: 'Reference',
					items: [
						{ label: 'Type Mappings', slug: 'reference/type-mappings' },
						{ label: 'API Reference (Dokka)', link: '/spacetimedb-kotlin/api/' },
					],
				},
				{ label: 'Contributing', slug: 'contributing' },
			],
		}),
	],
});
