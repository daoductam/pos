---
name: optimize-bundle
description: You MUST use this when the user asks to optimize bundle size, analyze frontend build size, reduce bundle, improve performance, or detect heavy dependencies.
---

# System Role & Goal
Analyze the frontend project and identify opportunities to reduce bundle size and improve runtime performance. This workflow focuses on detecting heavy dependencies, inefficient imports, missing lazy loading, large bundle chunks, and suboptimal build configurations.

# Context & Constraints
## Supported Environments
- **Toolchains:** Vite, Webpack, Rspack, Next.js, Create React App.
- **Output Folders:** `dist/`, `build/`, `.next/`.

## Optimization Targets & Limits
- **Known Heavy Dependencies:** `moment`, `lodash`, `chart.js`, `react-icons`, `antd`.
- **Static Assets Limits:** Images should be <300KB (prefer WebP/AVIF formats), Fonts <100KB (prefer WOFF2).
- **Strict Constraints:** When applying optimizations, you MUST NOT modify business logic, avoid breaking changes, and only optimize imports and dependencies.

# Instructions (Standard Operating Procedure)

**Step 1: Project Detection & Build**
- Identify the frontend framework by inspecting configuration files (e.g., `vite.config.ts`, `webpack.config.js`, `next.config.js`, `angular.json`).
- Run the correct build command based on the detected framework (e.g., `npm run build`, `ng build`).

**Step 2: Measure Initial Bundle Size**
- Locate the build output folder.
- Sum all JavaScript bundle files, focusing specifically on large `vendor.js` and `main.js` chunks.
- Record total bundle size, largest chunk size, and vendor bundle size.

**Step 3: Dependency & Code Analysis**
- Inspect `package.json` for heavy dependencies and prefer modular imports instead of entire libraries.
- Search source code for inefficient import patterns (e.g., `import _ from "lodash"`).
- Identify missing code splitting, missing lazy loading, and non-tree-shakeable imports.

**Step 4: Static Assets Analysis**
- Scan directories like `public/` or `src/assets/` for uncompressed images or files exceeding 300KB.
- Recommend format improvements (e.g., PNG/JPEG to WebP/AVIF, GIF to MP4/WebM).

**Step 5: Apply Safe Optimizations**
- Convert full library imports to modular imports.
- Replace heavy dependencies with lightweight alternatives (e.g., `moment` → `dayjs`).
- Implement lazy loading for heavy components and route pages (e.g., `const HeavyComponent = lazy(() => import("./HeavyComponent"))`).

**Step 6: Verification & Reporting**
- Rebuild the project (`npm run build`) and measure the bundle size again.
- Generate a bundle analysis report comparing the "Before" and "After" metrics (Total Bundle, Main Chunk, Vendor Chunk).
- Output a "Largest Modules" table detailing the module size, reason for size, and suggested fixes.