import type { Page } from "@playwright/test";

/** Wait for finite CSS motion to settle without stopping infinite indicators. */
export async function waitForVisualTransitions(page: Page): Promise<void> {
  await page.evaluate(async () => {
    const nextFrame = () =>
      new Promise<void>((resolve) => requestAnimationFrame(() => resolve()));
    let stableFrames = 0;

    while (stableFrames < 2) {
      await nextFrame();
      const motions = document.getAnimations().filter((animation) => {
        if (
          !(
            animation instanceof CSSTransition ||
            animation instanceof CSSAnimation
          ) ||
          animation.playState !== "running"
        ) {
          return false;
        }
        const endTime = animation.effect?.getComputedTiming().endTime;
        return typeof endTime === "number" && Number.isFinite(endTime);
      });

      if (motions.length === 0) {
        stableFrames += 1;
      } else {
        stableFrames = 0;
        await Promise.allSettled(
          motions.map((animation) => animation.finished),
        );
      }
    }
  });
}
