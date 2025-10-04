/**
 * FillBetweenPrimitive - Fills area between close price and an indicator line
 * Similar to Pine Script's fill() function
 * 
 * Usage:
 * const fillPrimitive = new FillBetweenPrimitive(chart, series, indicatorData, options);
 * series.attachPrimitive(fillPrimitive);
 */

export class FillBetweenPrimitive {
    constructor(chart, series, indicatorData, options = {}) {
        this._chart = chart;
        this._series = series;
        this._indicatorData = indicatorData; // Array of {time, value} points
        this._options = {
            fillColor: options.fillColor || 'rgba(41, 98, 255, 0.1)',
            upFillColor: options.upFillColor || 'rgba(76, 175, 80, 0.15)', // When price > indicator
            downFillColor: options.downFillColor || 'rgba(239, 83, 80, 0.15)', // When price < indicator
            neutralFillColor: options.neutralFillColor || 'rgba(158, 158, 158, 0.1)',
            colorMode: options.colorMode || 'dynamic', // 'static', 'dynamic', 'conditional'
            ...options
        };
        
        // Convert indicator data to a map for quick lookup
        this._indicatorMap = new Map();
        if (indicatorData && Array.isArray(indicatorData)) {
            indicatorData.forEach(point => {
                this._indicatorMap.set(point.time, point.value);
            });
        }
    }

    updateAllViews() {
        // Called when chart needs to redraw
    }

    renderer() {
        return {
            draw: (target) => {
                target.useBitmapCoordinateSpace(scope => this._drawImpl(scope));
            }
        };
    }

    _drawImpl(scope) {
        const ctx = scope.context;
        const timeScale = this._chart.timeScale();
        const series = this._series;
        
        if (!series) return;

        try {
            const visibleRange = timeScale.getVisibleLogicalRange();
            if (!visibleRange) return;

            const priceScale = series.priceScale();
            const data = series.data();
            
            if (!data || data.length === 0) return;

            // Get visible data points
            const from = Math.max(0, Math.floor(visibleRange.from) - 1);
            const to = Math.min(data.length - 1, Math.ceil(visibleRange.to) + 1);

            ctx.save();

            // Draw filled areas between price and indicator
            for (let i = from; i < to; i++) {
                const currentPoint = data[i];
                const nextPoint = data[i + 1];
                
                if (!currentPoint || !nextPoint) continue;

                const currentTime = currentPoint.time;
                const nextTime = nextPoint.time;
                
                // Get close prices (use close for candlesticks, value for line series)
                const currentPrice = currentPoint.close !== undefined ? currentPoint.close : currentPoint.value;
                const nextPrice = nextPoint.close !== undefined ? nextPoint.close : nextPoint.value;
                
                // Get indicator values
                const currentIndicator = this._indicatorMap.get(currentTime);
                const nextIndicator = this._indicatorMap.get(nextTime);
                
                if (currentIndicator === undefined || nextIndicator === undefined) continue;
                if (currentPrice === undefined || nextPrice === undefined) continue;

                // Convert to screen coordinates
                const x1 = timeScale.timeToCoordinate(currentTime);
                const x2 = timeScale.timeToCoordinate(nextTime);
                
                if (x1 === null || x2 === null) continue;

                const y1Price = priceScale.priceToCoordinate(currentPrice);
                const y1Indicator = priceScale.priceToCoordinate(currentIndicator);
                const y2Price = priceScale.priceToCoordinate(nextPrice);
                const y2Indicator = priceScale.priceToCoordinate(nextIndicator);
                
                if (y1Price === null || y1Indicator === null || y2Price === null || y2Indicator === null) continue;

                // Determine fill color based on mode
                let fillColor = this._options.fillColor;
                
                if (this._options.colorMode === 'dynamic') {
                    // Color based on whether price is above or below indicator
                    if (currentPrice > currentIndicator) {
                        fillColor = this._options.upFillColor;
                    } else if (currentPrice < currentIndicator) {
                        fillColor = this._options.downFillColor;
                    } else {
                        fillColor = this._options.neutralFillColor;
                    }
                } else if (this._options.colorMode === 'conditional' && this._options.getColor) {
                    // Use custom color function
                    fillColor = this._options.getColor(currentPoint, currentIndicator, i) || fillColor;
                }

                // Scale coordinates to bitmap space
                const scaledX1 = x1 * scope.horizontalPixelRatio;
                const scaledX2 = x2 * scope.horizontalPixelRatio;
                const scaledY1Price = y1Price * scope.verticalPixelRatio;
                const scaledY1Indicator = y1Indicator * scope.verticalPixelRatio;
                const scaledY2Price = y2Price * scope.verticalPixelRatio;
                const scaledY2Indicator = y2Indicator * scope.verticalPixelRatio;

                // Draw filled polygon
                ctx.fillStyle = fillColor;
                ctx.beginPath();
                ctx.moveTo(scaledX1, scaledY1Price);
                ctx.lineTo(scaledX2, scaledY2Price);
                ctx.lineTo(scaledX2, scaledY2Indicator);
                ctx.lineTo(scaledX1, scaledY1Indicator);
                ctx.closePath();
                ctx.fill();
            }

            ctx.restore();
        } catch (error) {
            console.error('Error drawing fill between primitive:', error);
        }
    }

    /**
     * Update the indicator data
     */
    updateIndicatorData(indicatorData) {
        this._indicatorData = indicatorData;
        this._indicatorMap.clear();
        
        if (indicatorData && Array.isArray(indicatorData)) {
            indicatorData.forEach(point => {
                this._indicatorMap.set(point.time, point.value);
            });
        }
        
        this._series.requestUpdate();
    }

    /**
     * Update fill options
     */
    updateOptions(options) {
        this._options = {
            ...this._options,
            ...options
        };
        this._series.requestUpdate();
    }
}

