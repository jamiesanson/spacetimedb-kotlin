// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';

// https://astro.build/config
export default defineConfig({
	integrations: [
		starlight({
			title: 'SpacetimeDB Kotlin SDK',
			social: [{ icon: 'github', label: 'GitHub', href: 'https://github.com/nicksanson/spacetimedb-kotlin' }],
			sidebar: [
				{ label: 'Getting Started', slug: 'getting-started' },
				{
					label: 'Guides',
					items: [
						{ label: 'SDK Reference', slug: 'guides/sdk-reference' },
						{ label: 'Generated Code', slug: 'guides/codegen' },
						{ label: 'Gradle Plugin', slug: 'guides/gradle-plugin' },
					],
				},
				{
					label: 'Reference',
					items: [
						{ label: 'Type Mappings', slug: 'reference/type-mappings' },
						{ label: 'API Docs (Dokka)', link: '/api/' },
					],
				},
				{ label: 'Contributing', slug: 'contributing' },
			],
		}),
	],
});
