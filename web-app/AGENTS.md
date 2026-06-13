# Project rules

## Scope

Only work on:

- apps/pos-web
- package/ui when a component is truly reusable

Do not modify:

- apps/customer-web

## Stack

- React
- TypeScript
- Vite
- pnpm workspace
- Keycloak
- CSS Modules
- Tailwind CSS

## Architecture

- Pages belong in features/*/pages
- Feature-specific components belong in features/*
- Generic reusable UI belongs in package/ui
- Do not put the entire page in App.tsx
- Do not call fetch directly inside presentational components
- Do not duplicate shared types

## Figma implementation

- Read one Figma screen at a time
- Use the exact provided node ID
- Use Figma as visual reference, not as application architecture
- Convert generated Tailwind reference code to CSS Modules
- Download Figma assets into the repository
- Do not keep temporary Figma asset URLs in production code
- Preserve aspect ratios
- Build reusable components for repeated patterns
- Avoid absolute positioning except for overlays or decorative assets

## POS behavior

- Desktop-first
- The page must fill the viewport
- Sidebar is fixed-width
- Product area is flexible
- Checkout panel has a stable width
- Support screen widths from 1280px upward
- Add graceful behavior below 1280px
- Keyboard shortcuts must not fire while typing in an input

## Authentication

- Use the existing @hvt/auth package
- Do not instantiate multiple Keycloak clients
- Do not store access tokens manually in localStorage
- Do not implement custom refresh-token logic outside the auth package

## Quality gates

Before completing a task, run:

pnpm --filter pos-web build
pnpm --filter pos-web lint