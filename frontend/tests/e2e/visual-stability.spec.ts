import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";
import { waitForVisualTransitions } from "../helpers/visual-stability";

test("waits for finite CSS motion and chained transitions while an infinite indicator keeps running", async ({
  page,
}) => {
  await page.setContent(`
    <!doctype html>
    <html lang="en">
      <head>
        <title>Visual stability fixture</title>
        <style>
          body { background: #fff; color: #141414; }
          @keyframes enter { from { opacity: 0.2; } to { opacity: 1; } }
          @keyframes spin { to { transform: rotate(360deg); } }
          #content {
            background-color: #808080;
            transition: background-color 0.15s linear;
          }
          #content.enter { animation: enter 0.4s linear; }
          #indicator {
            display: inline-block;
            animation: spin 1s linear infinite;
          }
        </style>
      </head>
      <body>
        <main>
          <h1>Visual stability</h1>
          <p id="content">The audit must use the final colors.</p>
          <span id="indicator" aria-hidden="true">*</span>
        </main>
      </body>
    </html>
  `);
  await page.evaluate(() => {
    const content = document.getElementById("content")!;
    content.addEventListener(
      "animationend",
      () => {
        content.addEventListener(
          "transitionend",
          () => {
            content.dataset.settled = "true";
          },
          { once: true },
        );
        content.style.backgroundColor = "#fff";
      },
      { once: true },
    );
    content.classList.add("enter");
  });

  await waitForVisualTransitions(page);
  const state = await page.evaluate(() => {
    const content = document.getElementById("content")!;
    const indicator = document.getElementById("indicator")!;
    return {
      settled: content.dataset.settled ?? null,
      background: getComputedStyle(content).backgroundColor,
      opacity: getComputedStyle(content).opacity,
      infiniteIndicatorRunning: indicator
        .getAnimations()
        .some((animation) => animation.playState === "running"),
    };
  });
  expect(state).toEqual({
    settled: "true",
    background: "rgb(255, 255, 255)",
    opacity: "1",
    infiniteIndicatorRunning: true,
  });
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
});
