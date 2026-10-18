import type { Page } from "@playwright/test";

/** Audit the resulting UI, not intermediate colors of an active CSS transition. */
export async function waitForVisualTransitions(page: Page): Promise<void> {
  await page.evaluate(async () => {
    const transitions = document
      .getAnimations()
      .filter((animation) => animation instanceof CSSTransition);
    await Promise.allSettled(
      transitions.map((animation) => animation.finished),
    );
  });
}
