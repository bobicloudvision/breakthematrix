/**
 * DeltaTablePrimitive - Draws delta metrics table below candles
 * Shows Delta, Cum Delta, and Delta Change aligned with each candle
 */

class DeltaTablePaneView {
    constructor(source) {
        this._source = source;
    }

    update() {
        // Called when data changes
    }

    renderer() {
        return this._source._renderer;
    }
}

class DeltaTableRenderer {
    constructor(source) {
        this._source = source;
    }

    draw(target) {
        target.useBitmapCoordinateSpace(scope => this._drawImpl(scope, target));
    }

    _drawImpl(scope, target) {
        const ctx = scope.context;
        const data = this._source._data;

        if (!data || data.length === 0) {
            return;
        }

        const chart = this._source._chart;
        const series = this._source._series;
        
        if (!chart || !series) {
            return;
        }

        ctx.save();
        
        const pixelRatio = scope.horizontalPixelRatio || 1;
        const timeScale = chart.timeScale();
        
        if (!timeScale) {
            ctx.restore();
            return;
        }

        const visibleRange = timeScale.getVisibleLogicalRange();
        if (!visibleRange) {
            ctx.restore();
            return;
        }

        // Get bar spacing
        const barSpacing = timeScale.options().barSpacing || 6;
        const candleWidth = barSpacing * 0.95 * pixelRatio;

        // Table configuration
        const rowHeight = 20 * pixelRatio;
        const fontSize = 9 * pixelRatio;
        const tableTop = scope.bitmapSize.height - (rowHeight * 3); // 3 rows at bottom

        // Draw each candle's data
        data.forEach((candleData, index) => {
            const x = timeScale.timeToCoordinate(candleData.time);
            if (x === null || x === undefined) {
                return;
            }
            
            const scaledX = x * pixelRatio;
            const candleLeft = scaledX - candleWidth / 2;
            const candleRight = scaledX + candleWidth / 2;
            const cellCenterX = scaledX;

            // Calculate values
            const delta = candleData.delta || 0;
            const cumDelta = candleData.cumulativeDelta || 0;
            const prevCandle = index > 0 ? data[index - 1] : null;
            const deltaChange = prevCandle ? delta - (prevCandle.delta || 0) : 0;

            ctx.font = `${fontSize}px -apple-system, BlinkMacSystemFont, monospace`;
            ctx.textAlign = 'center';
            ctx.textBaseline = 'middle';

            // Row 1: Delta
            const row1Y = tableTop + rowHeight / 2;
            const deltaColor = delta >= 0 ? 'rgba(16, 185, 129, 0.15)' : 'rgba(239, 68, 68, 0.15)';
            ctx.fillStyle = deltaColor;
            ctx.fillRect(candleLeft, tableTop, candleWidth, rowHeight);
            
            ctx.fillStyle = delta >= 0 ? 'rgb(16, 185, 129)' : 'rgb(239, 68, 68)';
            const deltaText = (delta >= 0 ? '+' : '') + delta.toFixed(1);
            ctx.fillText(deltaText, cellCenterX, row1Y);

            // Row 2: Cumulative Delta
            const row2Y = tableTop + rowHeight + rowHeight / 2;
            const cumDeltaColor = cumDelta >= 0 ? 'rgba(16, 185, 129, 0.15)' : 'rgba(239, 68, 68, 0.15)';
            ctx.fillStyle = cumDeltaColor;
            ctx.fillRect(candleLeft, tableTop + rowHeight, candleWidth, rowHeight);
            
            ctx.fillStyle = cumDelta >= 0 ? 'rgb(16, 185, 129)' : 'rgb(239, 68, 68)';
            const cumDeltaText = (cumDelta >= 0 ? '+' : '') + cumDelta.toFixed(1);
            ctx.fillText(cumDeltaText, cellCenterX, row2Y);

            // Row 3: Delta Change
            const row3Y = tableTop + rowHeight * 2 + rowHeight / 2;
            const deltaChangeColor = deltaChange >= 0 ? 'rgba(16, 185, 129, 0.15)' : 'rgba(239, 68, 68, 0.15)';
            ctx.fillStyle = deltaChangeColor;
            ctx.fillRect(candleLeft, tableTop + rowHeight * 2, candleWidth, rowHeight);
            
            ctx.fillStyle = deltaChange >= 0 ? 'rgb(16, 185, 129)' : 'rgb(239, 68, 68)';
            const deltaChangeText = (deltaChange >= 0 ? '+' : '') + deltaChange.toFixed(1);
            ctx.fillText(deltaChangeText, cellCenterX, row3Y);

            // Draw cell borders
            ctx.strokeStyle = 'rgba(100, 100, 100, 0.3)';
            ctx.lineWidth = 1 * pixelRatio;
            ctx.strokeRect(candleLeft, tableTop, candleWidth, rowHeight * 3);
        });

        // Draw row labels on the right
        const labelWidth = 60 * pixelRatio;
        const labelX = scope.bitmapSize.width - labelWidth;
        
        ctx.fillStyle = 'rgba(30, 41, 59, 0.95)';
        ctx.fillRect(labelX, tableTop, labelWidth, rowHeight * 3);

        ctx.font = `bold ${fontSize}px -apple-system, BlinkMacSystemFont, sans-serif`;
        ctx.fillStyle = 'rgb(148, 163, 184)';
        ctx.textAlign = 'center';
        
        ctx.fillText('DELTA', labelX + labelWidth / 2, tableTop + rowHeight / 2);
        ctx.fillText('CUM Δ', labelX + labelWidth / 2, tableTop + rowHeight + rowHeight / 2);
        ctx.fillText('Δ CHNG', labelX + labelWidth / 2, tableTop + rowHeight * 2 + rowHeight / 2);

        // Draw label border
        ctx.strokeStyle = 'rgba(100, 100, 100, 0.5)';
        ctx.lineWidth = 1 * pixelRatio;
        ctx.strokeRect(labelX, tableTop, labelWidth, rowHeight * 3);

        ctx.restore();
    }
}

export class DeltaTablePrimitive {
    constructor(chart, series) {
        this._chart = chart;
        this._series = series;
        this._data = [];
        this._renderer = new DeltaTableRenderer(this);
        this._paneViews = [new DeltaTablePaneView(this)];
    }

    updateData(data) {
        this._data = data;
    }

    updateAllViews() {
        this._paneViews.forEach(pw => pw.update());
    }

    paneViews() {
        return this._paneViews;
    }
}

